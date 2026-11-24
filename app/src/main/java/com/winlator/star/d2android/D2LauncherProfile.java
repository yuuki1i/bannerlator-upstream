package com.winlator.star.d2android;

import java.io.File;

/**
 * Launch profile derived from the user's Collector's Edition launcher files.
 * The supplied batch file is: start game.exe -3dfx -direct
 *
 * The launcher EXE is a Windows wrapper. The batch file establishes the reliable
 * game target and renderer flags; Wine should launch game.exe from the game folder
 * with the same working directory, not try to execute a Windows .bat as an ELF process.
 */
public final class D2LauncherProfile {
    public static final String TARGET_EXE = "game.exe";
    public static final String LAUNCHER_EXE = "启动游戏.exe";
    public static final String LAUNCHER_BAT = "启动游戏.bat";
    public static final String ARGUMENTS = "-3dfx -direct";
    public static final String BUILD_ID = D2CompatibilityGate.TARGET_BUILD;

    private D2LauncherProfile() {}

    public static boolean isCollectorLauncher(File file) {
        if (file == null) return false;
        String name = file.getName();
        return LAUNCHER_EXE.equalsIgnoreCase(name) || LAUNCHER_BAT.equalsIgnoreCase(name);
    }

    /** Returns the executable path to register as the Wine shortcut target. */
    public static File resolveGameExecutable(File gameDirectory) {
        if (gameDirectory == null) throw new IllegalArgumentException("Game directory required");
        File executable = new File(gameDirectory, TARGET_EXE);
        if (!executable.isFile()) throw new IllegalArgumentException("game.exe not found beside the supplied launcher");
        return executable;
    }

    /** Exact arguments taken from 启动游戏.bat. */
    public static String getArguments() { return ARGUMENTS; }

    /** Launcher and game must share the same working directory for relative DLL/MPQ lookup. */
    public static File getWorkingDirectory(File gameDirectory) {
        if (gameDirectory == null || !gameDirectory.isDirectory())
            throw new IllegalArgumentException("Valid game directory required");
        return gameDirectory;
    }
}
