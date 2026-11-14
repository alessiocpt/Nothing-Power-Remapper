package io.github.alessiocpt.powerremapper.unlock;

/** Runs in the Shizuku user service process, which has shell privileges. */
interface IShellService {
    // Transaction code reserved by Shizuku for tearing the user service process down.
    void destroy() = 16777114;

    boolean setPackageEnabled(String packageName, boolean enabled, int userId) = 1;

    boolean injectKey(int keyCode) = 2;
}
