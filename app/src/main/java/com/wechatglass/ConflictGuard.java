package com.wechatglass;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Detects the original WeChat-LiquidGlass module and removes only its
 * {@code com.tencent.mm} scope, leaving every other target (QQ) untouched.
 *
 * <p>Disabling the whole module would break users who also run it on QQ, so the
 * guard never calls {@code module disable}. It reads the LSPosed module and
 * scope tables through the debug CLI, removes only the WeChat entry, and lets
 * the user restart WeChat.
 */
final class ConflictGuard {

    static final String ORIGINAL = "io.github.liuran001.mmliquidglass";
    private static final String WECHAT = "com.tencent.mm";
    private static final String QQ = "com.tencent.mobileqq";

    private static final String[] LSPCTL_PATHS = {
            "/data/adb/modules/zygisk_lsposed/lspctl",
            "/data/adb/modules/riru_lsposed/lspctl",
            "/data/adb/modules/lsposed/lspctl",
            "/data/adb/lspd/lspctl"
    };

    static final class Status {
        boolean installed;
        boolean root;
        boolean enabled0;
        boolean enabled10;
        boolean wechat0;
        boolean wechat10;
        boolean qq0;
        boolean qq10;
        String lspctl;
        String raw = "";

        boolean conflict() {
            return (enabled0 && wechat0) || (enabled10 && wechat10);
        }

        boolean wechatScope() {
            return wechat0 || wechat10;
        }
    }

    private ConflictGuard() {
    }

    static boolean installed(Context context) {
        try {
            context.getPackageManager().getPackageInfo(ORIGINAL, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Status detect(Context context) {
        Status s = new Status();
        s.installed = installed(context);
        if (!s.installed) {
            return s;
        }
        String id = su("id");
        s.root = id != null && id.contains("uid=0");
        if (!s.root) {
            android.util.Log.i("WeChatGlass", "conflict detect: no root from module app");
            return s;
        }
        s.lspctl = findLspctl();
        if (s.lspctl == null) {
            return s;
        }
        String modules = su(s.lspctl + " module list");
        s.raw = modules == null ? "" : modules;
        if (modules != null) {
            for (String line : modules.split("\n")) {
                String[] c = line.trim().split("\\s+");
                if (c.length < 3 || !ORIGINAL.equals(c[2])) {
                    continue;
                }
                int user = parseInt(c[1]);
                boolean on = "enabled".equals(c[0]);
                if (user == 0) {
                    s.enabled0 = on;
                } else if (user == 10) {
                    s.enabled10 = on;
                }
            }
        }
        readScope(s, 0);
        readScope(s, 10);
        android.util.Log.i("WeChatGlass", "conflict detect installed=" + s.installed
                + " lspctl=" + s.lspctl + " root=" + s.root
                + " enabled=" + s.enabled0 + "/" + s.enabled10
                + " wechat=" + s.wechat0 + "/" + s.wechat10
                + " qq=" + s.qq0 + "/" + s.qq10);
        return s;
    }

    private static void readScope(Status s, int user) {
        if (s.lspctl == null) {
            return;
        }
        String out = su(s.lspctl + " scope list " + ORIGINAL + " --user=" + user);
        if (out == null) {
            return;
        }
        for (String line : out.split("\n")) {
            String t = line.trim();
            if (t.endsWith("\t" + WECHAT) || t.endsWith(" " + WECHAT)) {
                if (user == 0) {
                    s.wechat0 = true;
                } else {
                    s.wechat10 = true;
                }
            }
            if (t.endsWith("\t" + QQ) || t.endsWith(" " + QQ)) {
                if (user == 0) {
                    s.qq0 = true;
                } else {
                    s.qq10 = true;
                }
            }
        }
    }

    /**
     * Remove only the WeChat scope from the original module. QQ and any other
     * target stay exactly as the user configured them.
     */
    static boolean disableWeChatScope(Context context) {
        String lspctl = findLspctl();
        if (lspctl == null) {
            return false;
        }
        boolean any = false;
        for (int user : new int[]{0, 10}) {
            String out = su(lspctl + " scope remove --user=" + user + " "
                    + ORIGINAL + " " + WECHAT);
            if (out != null && out.toLowerCase().contains("updated")) {
                any = true;
            }
        }
        return any;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String findLspctl() {
        StringBuilder script = new StringBuilder("for p in");
        for (String p : LSPCTL_PATHS) {
            script.append(' ').append(p);
        }
        script.append("; do if [ -x \"$p\" ]; then echo \"$p\"; break; fi; done");
        String out = su(script.toString());
        if (out == null) {
            return null;
        }
        for (String line : out.split("\n")) {
            String t = line.trim();
            if (t.startsWith("/data/adb/")) {
                return t;
            }
        }
        return null;
    }

    private static String su(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            StringBuilder out = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            BufferedReader err = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));
            while ((line = err.readLine()) != null) {
                out.append(line).append('\n');
            }
            process.waitFor();
            String result = out.toString();
            android.util.Log.i("WeChatGlass", "conflict su [" + command + "] => " + result);
            return result;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
