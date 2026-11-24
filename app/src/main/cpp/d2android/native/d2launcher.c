#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <tlhelp32.h>

static int fail(const char* message, DWORD code) {
    char text[512];
    _snprintf(text,sizeof(text),"%s (error %lu)",message,(unsigned long)code);
    MessageBoxA(NULL,text,"D2Android launcher",MB_ICONERROR|MB_OK);
    return 1;
}

int WINAPI WinMain(HINSTANCE instance,HINSTANCE previous,LPSTR commandLine,int show) {
    (void)instance; (void)previous; (void)show;
    char cwd[MAX_PATH], game[MAX_PATH], mod[MAX_PATH], wrapper[MAX_PATH], cmd[2048];
    if (!GetCurrentDirectoryA(sizeof(cwd),cwd)) return fail("Cannot read game directory",GetLastError());
    if (!GetModuleFileNameA(NULL,wrapper,sizeof(wrapper))) return fail("Cannot locate launcher",GetLastError());
    char* slash=strrchr(wrapper,'\\');
    if (!slash) return fail("Invalid launcher path",ERROR_BAD_PATHNAME);
    *slash=0;
    _snprintf(game,sizeof(game),"%s\\game.exe",cwd);
    _snprintf(mod,sizeof(mod),"%s\\d2android_mod.dll",wrapper);
    if (GetFileAttributesA(game)==INVALID_FILE_ATTRIBUTES) return fail("game.exe was not found beside the launcher",ERROR_FILE_NOT_FOUND);
    if (GetFileAttributesA(mod)==INVALID_FILE_ATTRIBUTES) return fail("d2android_mod.dll was not found beside the launcher",ERROR_FILE_NOT_FOUND);

    _snprintf(cmd,sizeof(cmd),"\"%s\" %s",game,commandLine?commandLine:"");
    STARTUPINFOA si; PROCESS_INFORMATION pi;
    ZeroMemory(&si,sizeof(si)); ZeroMemory(&pi,sizeof(pi)); si.cb=sizeof(si);
    if (!CreateProcessA(game,cmd,NULL,NULL,FALSE,CREATE_SUSPENDED,NULL,cwd,&si,&pi))
        return fail("Could not start game.exe",GetLastError());

    SIZE_T bytes=strlen(mod)+1;
    LPVOID remote=VirtualAllocEx(pi.hProcess,NULL,bytes,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);
    if (!remote) { TerminateProcess(pi.hProcess,1); return fail("Could not allocate injection buffer",GetLastError()); }
    SIZE_T written=0;
    if (!WriteProcessMemory(pi.hProcess,remote,mod,bytes,&written) || written!=bytes) {
        DWORD error=GetLastError(); VirtualFreeEx(pi.hProcess,remote,0,MEM_RELEASE);
        TerminateProcess(pi.hProcess,1); return fail("Could not write module path",error);
    }
    HMODULE kernel=GetModuleHandleA("kernel32.dll");
    FARPROC load=kernel?GetProcAddress(kernel,"LoadLibraryA"):NULL;
    HANDLE snapshot=CreateToolhelp32Snapshot(TH32CS_SNAPMODULE|TH32CS_SNAPMODULE32,pi.dwProcessId);
    MODULEENTRY32 entry; ZeroMemory(&entry,sizeof(entry)); entry.dwSize=sizeof(entry);
    BYTE* remoteKernel=NULL;
    if (snapshot!=INVALID_HANDLE_VALUE && Module32First(snapshot,&entry)) {
        do {
            if (_stricmp(entry.szModule,"kernel32.dll")==0) {
                remoteKernel=(BYTE*)entry.modBaseAddr;
                break;
            }
        } while (Module32Next(snapshot,&entry));
    }
    if (snapshot!=INVALID_HANDLE_VALUE) CloseHandle(snapshot);
    if (!load || !remoteKernel) {
        VirtualFreeEx(pi.hProcess,remote,0,MEM_RELEASE);
        TerminateProcess(pi.hProcess,1); return fail("Could not resolve target LoadLibraryA",GetLastError());
    }
    DWORD loadOffset=(DWORD)((BYTE*)load-(BYTE*)kernel);
    LPTHREAD_START_ROUTINE remoteLoad=(LPTHREAD_START_ROUTINE)(remoteKernel+loadOffset);
    HANDLE inject=CreateRemoteThread(pi.hProcess,NULL,0,remoteLoad,remote,0,NULL);
    if (!inject) {
        DWORD error=GetLastError(); VirtualFreeEx(pi.hProcess,remote,0,MEM_RELEASE);
        TerminateProcess(pi.hProcess,1); return fail("Could not inject the 1.13c touch module",error);
    }
    DWORD waitResult=WaitForSingleObject(inject,10000);
    DWORD moduleResult=0;
    if (waitResult != WAIT_OBJECT_0) {
        CloseHandle(inject); VirtualFreeEx(pi.hProcess,remote,0,MEM_RELEASE);
        TerminateProcess(pi.hProcess,1); CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
        return fail("Timed out while loading the 1.13c touch module",ERROR_TIMEOUT);
    }
    GetExitCodeThread(inject,&moduleResult);
    CloseHandle(inject);
    VirtualFreeEx(pi.hProcess,remote,0,MEM_RELEASE);
    if (!moduleResult) {
        TerminateProcess(pi.hProcess,1); CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
        return fail("The 1.13c module refused this game build",ERROR_BAD_EXE_FORMAT);
    }

    ResumeThread(pi.hThread);
    CloseHandle(pi.hThread);
    WaitForSingleObject(pi.hProcess,INFINITE);
    DWORD exitCode=0; GetExitCodeProcess(pi.hProcess,&exitCode);
    CloseHandle(pi.hProcess);
    return (int)exitCode;
}
