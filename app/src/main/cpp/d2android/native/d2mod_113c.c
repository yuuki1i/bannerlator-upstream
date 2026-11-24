#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <windows.h>
#include <bcrypt.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/*
 * Diablo II LoD 1.13c touch module.
 *
 * RVAs are sourced from the 1.13c address table in slashdiablo-maphack/BH/D2Ptrs.h.
 * This module does not reuse the 1.13d hook patch or 1.13d RVAs. It runs game-thread
 * input and state updates independently from the Android overlay process.
 * work from a WH_GETMESSAGE hook and refuses to initialize unless the owner-supplied
 * Game.exe and D2Common.dll fingerprints match and D2Client.dll has the 1.13c version
 * metadata and expected file size.
 */

#define APP_PORT 7958
#define MOD_PORT 7959
#define PROTOCOL_COMMAND 1
#define PROTOCOL_STATE 2
#define D2AM_PROTOCOL_VERSION 3
#define STATUS_HOOKED 1
#define STATUS_WRONG_VERSION 2
#define STATUS_FAILED 3

#define CMD_UNIT 2
#define CMD_HIGHLIGHT 3
#define CMD_WALK 4
#define CMD_TAKE 15
#define CMD_KEY_PRESS 36
#define CMD_GROUND_SKILL 37
#define CMD_TAP 38

#define PHASE_DOWN 0
#define PHASE_HOLD 1
#define PHASE_UP 2
#define PHASE_CLICK 3

#define BUTTON_LEFT 0
#define BUTTON_RIGHT 1

#define RVA_GET_UNIT_X 0x00001630
#define RVA_GET_UNIT_Y 0x00001660
#define RVA_CLICK_MAP 0x0001BF20
#define RVA_GET_MOUSE_X_OFFSET 0x0003F6C0
#define RVA_GET_MOUSE_Y_OFFSET 0x0003F6D0
#define RVA_SET_SELECTED_UNIT 0x00051860
#define RVA_GET_SELECTED_UNIT 0x00051A80
#define RVA_GET_PLAYER_UNIT 0x000A4D60
#define RVA_FIND_SERVER_SIDE_UNIT 0x000A5B40
#define RVA_FIND_CLIENT_SIDE_UNIT 0x000A5B20
#define RVA_MOUSE_Y 0x0011B824
#define RVA_MOUSE_X 0x0011B828
#define RVA_VIEWPORT 0x0011C1F8
#define RVA_ALWAYS_RUN 0x0011C3EC
#define RVA_SCREEN_SIZE_X 0x000DBC48
#define RVA_SCREEN_SIZE_Y 0x000DBC4C

#define U_TYPE 0x00
#define U_ID 0x0C
#define U_MODE 0x10
#define U_TXTNO 0x20
#define U_PATH 0x2C
#define U_LISTNEXT 0xE8
#define PATH_ROOM1 0x1C
#define ROOM1_ROOM2 0x10
#define ROOM1_UNITS 0x74
#define ROOM1_NEXT 0x7C
#define ROOM2_LEVEL 0x58
#define LEVEL_NO 0x1D0

#define MAX_MONSTERS 40
#define MAX_ITEMS 32
#define MAX_OBJECTS 24

typedef struct UnitAny UnitAny;
typedef UnitAny* (__stdcall *GetPlayerUnitFn)(void);
typedef UnitAny* (__stdcall *GetSelectedUnitFn)(void);
typedef UnitAny* (__fastcall *FindUnitFn)(DWORD id, DWORD type);
typedef int (__fastcall *GetUnitCoordFn)(UnitAny* unit);
typedef DWORD (__fastcall *GetMouseOffsetFn)(void);
typedef void (__stdcall *ClickMapFn)(DWORD clickType, DWORD x, DWORD y, DWORD flags);
typedef void (__stdcall *MapToAbsScreenFn)(int* x, int* y);

typedef struct {
    DWORD id;
    short x, y;
    BYTE hp, flags, nameLength;
    char name[1];
} MonsterRecord;

typedef struct {
    DWORD id;
    short x, y;
    WORD kind;
    BYTE extra;
} ThingRecord;

static HMODULE g_module;
static HMODULE g_client;
static HMODULE g_common;
static SOCKET g_socket = INVALID_SOCKET;
static HHOOK g_hook;
static HWND g_window;
static DWORD g_thread_id;
static struct sockaddr_in g_app_addr;
static volatile LONG g_sequence;
static volatile LONG g_commands_received;
static volatile LONG g_ready;
static volatile LONG g_stop;
static DWORD g_last_status;
static DWORD g_last_state;

static GetPlayerUnitFn fGetPlayerUnit;
static GetSelectedUnitFn fGetSelectedUnit;
static FindUnitFn fFindServerSideUnit, fFindClientSideUnit;
static GetUnitCoordFn fGetUnitX, fGetUnitY;
static GetMouseOffsetFn fGetMouseXOffset, fGetMouseYOffset;
static ClickMapFn fClickMap;
static DWORD fSetSelectedUnit;
static MapToAbsScreenFn fMapToAbsScreen;

static struct {
    int active, stopping;
    int dx, dy;
    DWORD expires, lastPress;
} g_walk;

typedef struct {
    int active;
    int button;
    DWORD type, id, expires, lastPress;
} HeldAction;
static HeldAction g_held[2];
typedef struct {
    int active, button, dx, dy;
    DWORD expires, lastPress;
} GroundAction;
static GroundAction g_ground[2];
typedef struct {
    int active, button, x, y;
    DWORD expires;
} TapAction;
static TapAction g_tap[2];

static char g_game_hash[65];
static char g_common_hash[65];
static char g_status_text[256];

static DWORD now_ms(void) { return GetTickCount(); }

static void put_u16(BYTE* p, unsigned* n, unsigned v) {
    p[(*n)++] = (BYTE)v; p[(*n)++] = (BYTE)(v >> 8);
}
static void put_s16(BYTE* p, unsigned* n, int v) { put_u16(p, n, (unsigned)(short)v); }
static void put_u32(BYTE* p, unsigned* n, DWORD v) {
    p[(*n)++] = (BYTE)v; p[(*n)++] = (BYTE)(v >> 8);
    p[(*n)++] = (BYTE)(v >> 16); p[(*n)++] = (BYTE)(v >> 24);
}
static void put_u8(BYTE* p, unsigned* n, unsigned v) { p[(*n)++] = (BYTE)v; }

static int sha256_file(const char* path, char out[65]) {
    BCRYPT_ALG_HANDLE alg = NULL;
    BCRYPT_HASH_HANDLE hash = NULL;
    PUCHAR object = NULL;
    DWORD object_len = 0, digest_len = 0, got = 0;
    HANDLE file = INVALID_HANDLE_VALUE;
    BYTE digest[32], buffer[65536];
    int ok = 0;
    if (BCryptOpenAlgorithmProvider(&alg, BCRYPT_SHA256_ALGORITHM, NULL, 0) < 0) goto done;
    if (BCryptGetProperty(alg, BCRYPT_OBJECT_LENGTH, (PUCHAR)&object_len, sizeof(object_len), &got, 0) < 0) goto done;
    if (BCryptGetProperty(alg, BCRYPT_HASH_LENGTH, (PUCHAR)&digest_len, sizeof(digest_len), &got, 0) < 0 || digest_len != 32) goto done;
    object = (PUCHAR)HeapAlloc(GetProcessHeap(), 0, object_len);
    if (!object) goto done;
    if (BCryptCreateHash(alg, &hash, object, object_len, NULL, 0, 0) < 0) goto done;
    file = CreateFileA(path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                       NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) goto done;
    for (;;) {
        DWORD read_count = 0;
        if (!ReadFile(file, buffer, sizeof(buffer), &read_count, NULL)) goto done;
        if (read_count == 0) break;
        if (BCryptHashData(hash, buffer, read_count, 0) < 0) goto done;
    }
    if (BCryptFinishHash(hash, digest, sizeof(digest), 0) < 0) goto done;
    for (unsigned i=0;i<32;i++) sprintf(out + i*2, "%02x", (unsigned)digest[i]);
    out[64] = 0;
    ok = 1;
done:
    if (file != INVALID_HANDLE_VALUE) CloseHandle(file);
    if (hash) BCryptDestroyHash(hash);
    if (object) HeapFree(GetProcessHeap(), 0, object);
    if (alg) BCryptCloseAlgorithmProvider(alg, 0);
    return ok;
}

static int git_blob_sha1_file(const char* path, char out[41]) {
    BCRYPT_ALG_HANDLE alg = NULL;
    BCRYPT_HASH_HANDLE hash = NULL;
    PUCHAR object = NULL;
    DWORD object_len = 0, digest_len = 0, got = 0;
    HANDLE file = INVALID_HANDLE_VALUE;
    BYTE digest[20], buffer[65536];
    int ok = 0;
    if (BCryptOpenAlgorithmProvider(&alg, BCRYPT_SHA1_ALGORITHM, NULL, 0) < 0) goto done;
    if (BCryptGetProperty(alg, BCRYPT_OBJECT_LENGTH, (PUCHAR)&object_len, sizeof(object_len), &got, 0) < 0) goto done;
    if (BCryptGetProperty(alg, BCRYPT_HASH_LENGTH, (PUCHAR)&digest_len, sizeof(digest_len), &got, 0) < 0 || digest_len != 20) goto done;
    object = (PUCHAR)HeapAlloc(GetProcessHeap(), 0, object_len);
    if (!object) goto done;
    if (BCryptCreateHash(alg, &hash, object, object_len, NULL, 0, 0) < 0) goto done;
    file = CreateFileA(path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                       NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) goto done;
    LARGE_INTEGER file_size;
    if (!GetFileSizeEx(file, &file_size)) goto done;
    char prefix[64];
    int prefix_len = _snprintf(prefix, sizeof(prefix), "blob %llu", (unsigned long long)file_size.QuadPart);
    if (prefix_len <= 0 || prefix_len >= (int)sizeof(prefix)-1) goto done;
    prefix[prefix_len] = 0;
    if (BCryptHashData(hash, (PUCHAR)prefix, (ULONG)prefix_len+1, 0) < 0) goto done;
    for (;;) {
        DWORD read_count = 0;
        if (!ReadFile(file, buffer, sizeof(buffer), &read_count, NULL)) goto done;
        if (read_count == 0) break;
        if (BCryptHashData(hash, buffer, read_count, 0) < 0) goto done;
    }
    if (BCryptFinishHash(hash, digest, sizeof(digest), 0) < 0) goto done;
    for (unsigned i=0;i<20;i++) sprintf(out + i*2, "%02x", (unsigned)digest[i]);
    out[40] = 0;
    ok = 1;
done:
    if (file != INVALID_HANDLE_VALUE) CloseHandle(file);
    if (hash) BCryptDestroyHash(hash);
    if (object) HeapFree(GetProcessHeap(), 0, object);
    if (alg) BCryptCloseAlgorithmProvider(alg, 0);
    return ok;
}

static void get_module_dir(char out[MAX_PATH]) {
    GetModuleFileNameA(g_module, out, MAX_PATH);
    char* slash = strrchr(out, '\\');
    if (slash) *slash = 0;
}

static int verify_installation(void) {
    char dir[MAX_PATH], game[MAX_PATH], common[MAX_PATH], client[MAX_PATH], client_blob[41];
    WIN32_FILE_ATTRIBUTE_DATA attr;
    get_module_dir(dir);
    _snprintf(game, sizeof(game), "%s\\game.exe", dir);
    _snprintf(common, sizeof(common), "%s\\D2Common.dll", dir);
    _snprintf(client, sizeof(client), "%s\\D2Client.dll", dir);
    if (!sha256_file(game, g_game_hash) || strcmp(g_game_hash,
        "7baeda5e815fbe7a807d6a102aada579cd7a1b89383dd4a9b5d92f887ef165bf") != 0) return 0;
    if (!sha256_file(common, g_common_hash) || strcmp(g_common_hash,
        "b5c1e13c0c0e27b2a9c2f391ce8b7035549fdec505f8d779cb1e9ead1c7bf6e7") != 0) return 0;
    if (!GetFileAttributesExA(client, GetFileExInfoStandard, &attr)) return 0;
    ULARGE_INTEGER file_size;
    file_size.HighPart = attr.nFileSizeHigh;
    file_size.LowPart = attr.nFileSizeLow;
    if (file_size.QuadPart != 1093632ULL || !git_blob_sha1_file(client, client_blob)
            || strcmp(client_blob, "6f467b93842b170bcbde2c00308c8c07a5212a1e") != 0) return 0;

    g_client = GetModuleHandleA("D2Client.dll");
    g_common = GetModuleHandleA("D2Common.dll");
    if (!g_client || !g_common) return 0;

    fGetPlayerUnit = (GetPlayerUnitFn)((BYTE*)g_client + RVA_GET_PLAYER_UNIT);
    fGetSelectedUnit = (GetSelectedUnitFn)((BYTE*)g_client + RVA_GET_SELECTED_UNIT);
    fFindServerSideUnit = (FindUnitFn)((BYTE*)g_client + RVA_FIND_SERVER_SIDE_UNIT);
    fFindClientSideUnit = (FindUnitFn)((BYTE*)g_client + RVA_FIND_CLIENT_SIDE_UNIT);
    fGetUnitX = (GetUnitCoordFn)((BYTE*)g_client + RVA_GET_UNIT_X);
    fGetUnitY = (GetUnitCoordFn)((BYTE*)g_client + RVA_GET_UNIT_Y);
    fGetMouseXOffset = (GetMouseOffsetFn)((BYTE*)g_client + RVA_GET_MOUSE_X_OFFSET);
    fGetMouseYOffset = (GetMouseOffsetFn)((BYTE*)g_client + RVA_GET_MOUSE_Y_OFFSET);
    fClickMap = (ClickMapFn)((BYTE*)g_client + RVA_CLICK_MAP);
    fSetSelectedUnit = (DWORD)((BYTE*)g_client + RVA_SET_SELECTED_UNIT);
    fMapToAbsScreen = (MapToAbsScreenFn)GetProcAddress(g_common, (LPCSTR)11087);
    if (!fGetPlayerUnit || !fGetSelectedUnit || !fFindServerSideUnit || !fFindClientSideUnit
            || !fGetUnitX || !fGetUnitY || !fGetMouseXOffset || !fGetMouseYOffset
            || !fClickMap || !fSetSelectedUnit || !fMapToAbsScreen)
        return 0;
    _snprintf(g_status_text, sizeof(g_status_text), "D2Android-1.13c-native;game=%s;common=%s;client=%s",
              g_game_hash, g_common_hash, client_blob);
    return 1;
}

static int unit_screen(UnitAny* unit, int* sx, int* sy) {
    if (!unit || !fGetUnitX || !fGetUnitY || !fMapToAbsScreen) return 0;
    int x = fGetUnitX(unit), y = fGetUnitY(unit);
    fMapToAbsScreen(&x, &y);
    x -= fGetMouseXOffset();
    y -= fGetMouseYOffset();
    *sx = x; *sy = y;
    return 1;
}

static DWORD click_flags(void) {
    DWORD* always_run = (DWORD*)((BYTE*)g_client + RVA_ALWAYS_RUN);
    return (*always_run) ? 8u : 0u;
}

static void click_ground(DWORD click_type, int x, int y) {
    DWORD* mouse_x = (DWORD*)((BYTE*)g_client + RVA_MOUSE_X);
    DWORD* mouse_y = (DWORD*)((BYTE*)g_client + RVA_MOUSE_Y);
    DWORD old_x = *mouse_x, old_y = *mouse_y;
    *mouse_x = 0; *mouse_y = 0;
    fClickMap(click_type, (DWORD)x, (DWORD)y, click_flags());
    *mouse_x = old_x; *mouse_y = old_y;
}

static void walk_point(int dx, int dy, int* x, int* y) {
    UnitAny* player = fGetPlayerUnit();
    if (!player) { *x = 0; *y = 0; return; }
    *x = fGetUnitX(player); *y = fGetUnitY(player);
    fMapToAbsScreen(x, y);
    *x -= fGetMouseXOffset(); *y -= fGetMouseYOffset();
    *x += dx; *y += dy;
}

static void click_unit(DWORD click_type, int button, DWORD type, DWORD id) {
    UnitAny* unit = fFindServerSideUnit(id, type);
    if (!unit) unit = fFindClientSideUnit(id, type);
    if (!unit) return;
    int x, y;
    if (!unit_screen(unit, &x, &y)) return;
    DWORD a = (DWORD)(uintptr_t)unit, c = (DWORD)(uintptr_t)unit;
    __asm__ volatile("call *%2" : "+a"(a), "+c"(c) : "r"(fSetSelectedUnit) : "edx", "memory", "cc");
    DWORD* mouse_x = (DWORD*)((BYTE*)g_client + RVA_MOUSE_X);
    DWORD* mouse_y = (DWORD*)((BYTE*)g_client + RVA_MOUSE_Y);
    DWORD old_x = *mouse_x, old_y = *mouse_y;
    *mouse_x = 0; *mouse_y = 0;
    fClickMap(click_type, (DWORD)x, (DWORD)y, click_flags());
    *mouse_x = old_x; *mouse_y = old_y;
    a = 0; c = 0;
    __asm__ volatile("call *%2" : "+a"(a), "+c"(c) : "r"(fSetSelectedUnit) : "edx", "memory", "cc");
    (void)button;
}

static void release_held(int button) {
    HeldAction* held = &g_held[button];
    if (!held->active) return;
    click_unit(button == BUTTON_RIGHT ? 5u : 2u, button, held->type, held->id);
    memset(held, 0, sizeof(*held));
}

static void process_unit_command(const BYTE* p, unsigned length) {
    if (length < 20) return;
    int button = p[8], phase = p[9];
    DWORD type = *(const DWORD*)(p + 12);
    DWORD id = *(const DWORD*)(p + 16);
    if (button != BUTTON_LEFT && button != BUTTON_RIGHT) return;
    HeldAction* held = &g_held[button];
    if (phase == PHASE_UP) {
        if (held->active && held->id == id && held->type == type) release_held(button);
        return;
    }
    if (phase == PHASE_DOWN || phase == PHASE_HOLD) {
        if (held->active && (held->id != id || held->type != type)) release_held(button);
        held->active = 1; held->button = button; held->type = type; held->id = id;
        held->expires = now_ms() + 650;
        return;
    }
    if (phase == PHASE_CLICK) {
        click_unit(button == BUTTON_RIGHT ? 3u : 0u, button, type, id);
        click_unit(button == BUTTON_RIGHT ? 5u : 2u, button, type, id);
    }
}

static void process_command(const BYTE* p, unsigned length) {
    if (length < 8 || p[0] != 'D' || p[1] != '2' || p[2] != 'A' || p[3] != 'C') return;
    LONG cmd = p[5] & 0xFF;
    InterlockedIncrement(&g_commands_received);
    if (cmd == CMD_WALK && length >= 14) {
        int phase = p[8] & 0xFF;
        int dx = (short)(p[10] | (p[11] << 8));
        int dy = (short)(p[12] | (p[13] << 8));
        if (phase == PHASE_UP) {
            if (g_walk.active) { g_walk.active = 0; g_walk.stopping = 1; }
        } else if (phase == PHASE_DOWN || phase == PHASE_HOLD) {
            g_walk.active = 1; g_walk.stopping = 0;
            g_walk.dx = dx; g_walk.dy = dy; g_walk.expires = now_ms() + 650;
        }
    } else if (cmd == CMD_UNIT) {
        process_unit_command(p, length);
    } else if (cmd == CMD_TAKE && length >= 16) {
        DWORD type = p[8] & 0xFF;
        DWORD id = *(const DWORD*)(p + 12);
        click_unit(0u, BUTTON_LEFT, type, id);
        click_unit(2u, BUTTON_LEFT, type, id);
    } else if (cmd == CMD_KEY_PRESS && length >= 9) {
        BYTE vk = p[8];
        keybd_event(vk, 0, 0, 0);
        keybd_event(vk, 0, KEYEVENTF_KEYUP, 0);
    } else if (cmd == CMD_TAP && length >= 14) {
        int button=p[8], phase=p[9];
        if (button != BUTTON_LEFT && button != BUTTON_RIGHT) return;
        TapAction* tap=&g_tap[button];
        int x=(short)(p[10]|(p[11]<<8)), y=(short)(p[12]|(p[13]<<8));
        if (phase == PHASE_UP) {
            if (tap->active) click_ground(button==BUTTON_RIGHT?5u:2u,tap->x,tap->y);
            memset(tap,0,sizeof(*tap));
        } else if (phase == PHASE_DOWN) {
            if (!tap->active) click_ground(button==BUTTON_RIGHT?3u:0u,x,y);
            tap->active=1; tap->button=button; tap->x=x; tap->y=y; tap->expires=now_ms()+650;
        } else if (phase == PHASE_HOLD) {
            if (!tap->active) click_ground(button==BUTTON_RIGHT?3u:0u,x,y);
            else click_ground(button==BUTTON_RIGHT?4u:1u,x,y);
            tap->active=1; tap->button=button; tap->x=x; tap->y=y; tap->expires=now_ms()+650;
        } else if (phase == PHASE_CLICK) {
            click_ground(button==BUTTON_RIGHT?3u:0u,x,y);
            click_ground(button==BUTTON_RIGHT?5u:2u,x,y);
        }
    } else if (cmd == CMD_GROUND_SKILL && length >= 14) {
        int button = p[8], phase = p[9];
        if (button != BUTTON_LEFT && button != BUTTON_RIGHT) return;
        GroundAction* ground = &g_ground[button];
        if (phase == PHASE_UP) {
            if (ground->active) {
                int x,y; walk_point(ground->dx,ground->dy,&x,&y);
                click_ground(button==BUTTON_RIGHT?5u:2u,x,y);
                memset(ground,0,sizeof(*ground));
            }
        } else if (phase == PHASE_DOWN || phase == PHASE_HOLD) {
            ground->active=1; ground->button=button;
            ground->dx=(short)(p[10]|(p[11]<<8));
            ground->dy=(short)(p[12]|(p[13]<<8));
            ground->expires=now_ms()+650;
        } else if (phase == PHASE_CLICK) {
            int dx=(short)(p[10]|(p[11]<<8)), dy=(short)(p[12]|(p[13]<<8));
            int x,y; walk_point(dx,dy,&x,&y);
            click_ground(button==BUTTON_RIGHT?3u:0u,x,y);
            click_ground(button==BUTTON_RIGHT?5u:2u,x,y);
        }
    } else if (cmd == CMD_HIGHLIGHT && length >= 16) {
        /* The Android target selector keeps the lock; the game cursor remains untouched here. */
}

static void process_commands(void) {
    BYTE buffer[256];
    for (;;) {
        struct sockaddr_in source;
        int source_len = sizeof(source);
        int n = recvfrom(g_socket, (char*)buffer, sizeof(buffer), 0,
                         (struct sockaddr*)&source, &source_len);
        if (n == SOCKET_ERROR) {
            if (WSAGetLastError() == WSAEWOULDBLOCK) break;
            break;
        }
        if (n > 0) process_command(buffer, (unsigned)n);
    }
}

static int level_number(UnitAny* player) {
    if (!player) return 0;
    void* path = *(void**)((BYTE*)player + U_PATH);
    if (!path) return 0;
    void* room1 = *(void**)((BYTE*)path + PATH_ROOM1);
    if (!room1) return 0;
    void* room2 = *(void**)((BYTE*)room1 + ROOM1_ROOM2);
    if (!room2) return 0;
    void* level = *(void**)((BYTE*)room2 + ROOM2_LEVEL);
    if (!level) return 0;
    return *(DWORD*)((BYTE*)level + LEVEL_NO);
}

static int is_town_level(int level) {
    return level == 1 || level == 40 || level == 75 || level == 103 || level == 109;
}

static void collect_units(UnitAny* player, MonsterRecord monsters[MAX_MONSTERS], unsigned* monster_count,
                          ThingRecord items[MAX_ITEMS], unsigned* item_count,
                          ThingRecord objects[MAX_OBJECTS], unsigned* object_count, int in_town) {
    *monster_count = *item_count = *object_count = 0;
    if (!player) return;
    void* path = *(void**)((BYTE*)player + U_PATH);
    void* first_room = path ? *(void**)((BYTE*)path + PATH_ROOM1) : NULL;
    void* room = first_room;
    for (unsigned r=0; room && r<16; r++, room=*(void**)((BYTE*)room + ROOM1_NEXT)) {
        UnitAny* unit = *(UnitAny**)((BYTE*)room + ROOM1_UNITS);
        for (unsigned u=0; unit && u<128; u++, unit=*(UnitAny**)((BYTE*)unit + U_LISTNEXT)) {
            DWORD type = *(DWORD*)((BYTE*)unit + U_TYPE);
            DWORD id = *(DWORD*)((BYTE*)unit + U_ID);
            DWORD mode = *(DWORD*)((BYTE*)unit + U_MODE);
            int x, y;
            if (!id || !unit_screen(unit, &x, &y)) continue;
            if (type == 1 && mode <= 10 && *monster_count < MAX_MONSTERS) {
                MonsterRecord* m = &monsters[(*monster_count)++];
                m->id=id; m->x=(short)x; m->y=(short)y; m->hp=100;
                m->flags=in_town ? 16 : 0; m->nameLength=0;
            } else if (type == 4 && *item_count < MAX_ITEMS) {
                ThingRecord* t = &items[(*item_count)++];
                t->id=id; t->x=(short)x; t->y=(short)y;
                t->kind=(WORD)*(DWORD*)((BYTE*)unit + U_TXTNO); t->extra=0;
            } else if (type == 2 && *object_count < MAX_OBJECTS) {
                ThingRecord* t = &objects[(*object_count)++];
                t->id=id; t->x=(short)x; t->y=(short)y;
                t->kind=(WORD)*(DWORD*)((BYTE*)unit + U_TXTNO); t->extra=0;
            }
        }
    }
}

static void send_status(void) {
    BYTE p[320]; unsigned n=0;
    p[n++]='D'; p[n++]='2'; p[n++]='A'; p[n++]='M';
    p[n++]=D2AM_PROTOCOL_VERSION; p[n++]=1; put_u16(p,&n,(unsigned)InterlockedIncrement(&g_sequence));
    put_u8(p,&n,STATUS_HOOKED);
    unsigned text_len=(unsigned)strlen(g_status_text);
    if (text_len>240) text_len=240;
    put_u8(p,&n,text_len);
    memcpy(p+n,g_status_text,text_len); n+=text_len;
    sendto(g_socket,(const char*)p,(int)n,0,(struct sockaddr*)&g_app_addr,sizeof(g_app_addr));
}

static void send_state(void) {
    UnitAny* player = fGetPlayerUnit();
    int level=player ? level_number(player) : 0;
    int in_town=is_town_level(level);
    int player_x=0, player_y=0;
    if (player) unit_screen(player,&player_x,&player_y);
    UnitAny* hover=fGetSelectedUnit();
    DWORD hover_type=hover ? *(DWORD*)((BYTE*)hover+U_TYPE) : 0xFF;
    DWORD hover_id=hover ? *(DWORD*)((BYTE*)hover+U_ID) : 0xFFFFFFFF;
    MonsterRecord monsters[MAX_MONSTERS];
    ThingRecord items[MAX_ITEMS], objects[MAX_OBJECTS];
    unsigned monster_count=0,item_count=0,object_count=0;
    collect_units(player,monsters,&monster_count,items,&item_count,objects,&object_count,in_town);

    BYTE p[4096]; unsigned n=0;
    p[n++]='D'; p[n++]='2'; p[n++]='A'; p[n++]='M';
    p[n++]=D2AM_PROTOCOL_VERSION; p[n++]=2; put_u16(p,&n,(unsigned)InterlockedIncrement(&g_sequence));
    DWORD* width=(DWORD*)((BYTE*)g_client+RVA_SCREEN_SIZE_X);
    DWORD* height=(DWORD*)((BYTE*)g_client+RVA_SCREEN_SIZE_Y);
    put_u16(p,&n,*width ? *width : 800); put_u16(p,&n,*height ? *height : 600);
    put_s16(p,&n,*(int*)((BYTE*)g_client+RVA_MOUSE_X));
    put_s16(p,&n,*(int*)((BYTE*)g_client+RVA_MOUSE_Y));
    put_u32(p,&n,0); put_u32(p,&n,0);
    put_u16(p,&n,level); put_u8(p,&n,in_town); put_u8(p,&n,0);
    put_s16(p,&n,player_x); put_s16(p,&n,player_y);
    put_u8(p,&n,100); put_u8(p,&n,100);
    put_u8(p,&n,hover_type); put_u32(p,&n,hover_id);
    put_u8(p,&n,0); put_u8(p,&n,(unsigned)InterlockedCompareExchange(&g_commands_received,0,0));
    for (unsigned i=0;i<16;i++) put_u8(p,&n,0);
    put_u8(p,&n,monster_count); put_u8(p,&n,monster_count);
    for (unsigned i=0;i<monster_count;i++) {
        put_u32(p,&n,monsters[i].id); put_s16(p,&n,monsters[i].x); put_s16(p,&n,monsters[i].y);
        put_u8(p,&n,monsters[i].hp); put_u8(p,&n,monsters[i].flags); put_u8(p,&n,0);
    }
    put_u8(p,&n,item_count);
    for (unsigned i=0;i<item_count;i++) {
        put_u32(p,&n,items[i].id); put_s16(p,&n,items[i].x); put_s16(p,&n,items[i].y);
        put_u16(p,&n,items[i].kind); put_u8(p,&n,items[i].extra);
    }
    put_u8(p,&n,object_count);
    for (unsigned i=0;i<object_count;i++) {
        put_u32(p,&n,objects[i].id); put_s16(p,&n,objects[i].x); put_s16(p,&n,objects[i].y);
        put_u16(p,&n,objects[i].kind); put_u8(p,&n,objects[i].extra);
    }
    put_u8(p,&n,0); put_u16(p,&n,0xFFFF); put_u16(p,&n,0xFFFF);
    for (unsigned i=0;i<16;i++) { put_u16(p,&n,0xFFFF); put_u8(p,&n,0); }
    put_u8(p,&n,0);
    sendto(g_socket,(const char*)p,(int)n,0,(struct sockaddr*)&g_app_addr,sizeof(g_app_addr));
}

static void game_tick(void) {
    DWORD now=now_ms();
    process_commands();

    if (g_walk.active) {
        if ((LONG)(now-g_walk.expires)>=0) { g_walk.active=0; g_walk.stopping=1; }
        else {
            int x,y; walk_point(g_walk.dx,g_walk.dy,&x,&y);
            if (now-g_walk.lastPress>=90) {
                click_ground(0,x,y); g_walk.lastPress=now;
            } else click_ground(1,x,y);
        }
    }
    if (g_walk.stopping) {
        int x,y; walk_point(g_walk.dx,g_walk.dy,&x,&y);
        click_ground(2,x,y);
        g_walk.stopping=0;
    }

    for (int button=BUTTON_LEFT;button<=BUTTON_RIGHT;button++) {
        TapAction* tap=&g_tap[button];
        if (tap->active && (LONG)(now-tap->expires)>=0) {
            click_ground(button==BUTTON_RIGHT?5u:2u,tap->x,tap->y);
            memset(tap,0,sizeof(*tap));
        }
    }

    for (int button=BUTTON_LEFT;button<=BUTTON_RIGHT;button++) {
        GroundAction* ground = &g_ground[button];
        if (!ground->active) continue;
        int x,y; walk_point(ground->dx,ground->dy,&x,&y);
        if ((LONG)(now-ground->expires)>=0) {
            click_ground(button==BUTTON_RIGHT?5u:2u,x,y);
            memset(ground,0,sizeof(*ground));
        } else if (now-ground->lastPress>=90) {
            click_ground(button==BUTTON_RIGHT?3u:0u,x,y);
            ground->lastPress=now;
        } else {
            click_ground(button==BUTTON_RIGHT?4u:1u,x,y);
        }
    }

    for (int button=BUTTON_LEFT;button<=BUTTON_RIGHT;button++) {
        HeldAction* held = &g_held[button];
        if (!held->active) continue;
        if ((LONG)(now-held->expires)>=0) release_held(button);
        else if (now-held->lastPress>=90) {
            click_unit(button==BUTTON_RIGHT?3u:0u,button,held->type,held->id);
            held->lastPress=now;
        } else {
            click_unit(button==BUTTON_RIGHT?4u:1u,button,held->type,held->id);
        }
    }

    if (now-g_last_status>=1000) { send_status(); g_last_status=now; }
    if (now-g_last_state>=50) { send_state(); g_last_state=now; }
}

static LRESULT CALLBACK get_message_proc(int code, WPARAM wparam, LPARAM lparam) {
    if (code >= 0 && wparam == PM_REMOVE && InterlockedCompareExchange(&g_ready,0,0)) game_tick();
    return CallNextHookEx(g_hook,code,wparam,lparam);
}

static BOOL CALLBACK find_game_window(HWND hwnd, LPARAM param) {
    (void)param;
    DWORD pid=0;
    GetWindowThreadProcessId(hwnd,&pid);
    if (pid != GetCurrentProcessId() || !IsWindowVisible(hwnd)) return TRUE;
    char title[128]={0}, cls[128]={0};
    GetWindowTextA(hwnd,title,sizeof(title));
    GetClassNameA(hwnd,cls,sizeof(cls));
    if (title[0] || cls[0]) {
        g_window=hwnd;
        g_thread_id=GetWindowThreadProcessId(hwnd,NULL);
        return FALSE;
    }
    return TRUE;
}

static DWORD WINAPI module_worker(LPVOID unused) {
    (void)unused;
    for (unsigned i=0;i<600 && !InterlockedCompareExchange(&g_stop,0,0);i++) {
        g_client=GetModuleHandleA("D2Client.dll");
        g_common=GetModuleHandleA("D2Common.dll");
        if (g_client && g_common) break;
        Sleep(100);
    }
    if (!g_client || !g_common || !verify_installation()) return 0;

    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2,2),&wsa)!=0) return 0;
    g_socket=socket(AF_INET,SOCK_DGRAM,IPPROTO_UDP);
    if (g_socket==INVALID_SOCKET) { WSACleanup(); return 0; }
    u_long nonblocking=1;
    ioctlsocket(g_socket,FIONBIO,&nonblocking);
    struct sockaddr_in local;
    memset(&local,0,sizeof(local));
    local.sin_family=AF_INET; local.sin_addr.s_addr=htonl(INADDR_ANY); local.sin_port=htons(MOD_PORT);
    if (bind(g_socket,(struct sockaddr*)&local,sizeof(local))==SOCKET_ERROR) {
        closesocket(g_socket); g_socket=INVALID_SOCKET; WSACleanup(); return 0;
    }
    memset(&g_app_addr,0,sizeof(g_app_addr));
    g_app_addr.sin_family=AF_INET; g_app_addr.sin_addr.s_addr=htonl(INADDR_LOOPBACK); g_app_addr.sin_port=htons(APP_PORT);

    for (unsigned i=0;i<300 && !InterlockedCompareExchange(&g_stop,0,0);i++) {
        g_window=NULL; g_thread_id=0;
        EnumWindows(find_game_window,0);
        if (g_window && g_thread_id) break;
        Sleep(100);
    }
    if (!g_window || !g_thread_id) {
        closesocket(g_socket); g_socket=INVALID_SOCKET; WSACleanup(); return 0;
    }
    g_hook=SetWindowsHookExA(WH_GETMESSAGE,get_message_proc,g_module,g_thread_id);
    if (!g_hook) {
        closesocket(g_socket); g_socket=INVALID_SOCKET; WSACleanup(); return 0;
    }
    InterlockedExchange(&g_ready,1);
    send_status();
    return 0;
}

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID reserved) {
    (void)reserved;
    if (reason==DLL_PROCESS_ATTACH) {
        g_module=instance;
        DisableThreadLibraryCalls(instance);
        HANDLE thread=CreateThread(NULL,0,module_worker,NULL,0,NULL);
        if (thread) CloseHandle(thread);
    } else if (reason==DLL_PROCESS_DETACH) {
        InterlockedExchange(&g_stop,1);
        InterlockedExchange(&g_ready,0);
        if (g_hook) UnhookWindowsHookEx(g_hook);
        if (g_socket!=INVALID_SOCKET) closesocket(g_socket);
        WSACleanup();
    }
    return TRUE;
}
