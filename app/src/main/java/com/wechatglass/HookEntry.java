package com.wechatglass;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Scope is intentionally limited to com.tencent.mm and to the chat Activity's
 * lifecycle. No message, network, account or payment classes are touched.
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String WECHAT = "com.tencent.mm";
    private static final String CHATTING_UI = "com.tencent.mm.ui.chatting.ChattingUI";
    private static final String LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI";
    private static final String TAG = "WeChatGlass";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !WECHAT.equals(lpparam.packageName)) {
            return;
        }
        // Applies to every user that has WeChat installed (owner and secondary
        // profiles). Each profile gets its own process and its own hook state.
        hookHomeUiTopMargin(lpparam);
        hookExactBarVisibility(lpparam);
        hookChatFooterPanel(lpparam);

        try {
            XposedHelpers.findAndHookMethod(CHATTING_UI, lpparam.classLoader, "onCreate",
                    Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject instanceof Activity) {
                                    Activity activity = (Activity) param.thisObject;
                                    ChatGlassHook.attach(activity);
                                }
                            } catch (Throwable throwable) {
                                XposedBridge.log(TAG + " attach failed: " + throwable);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(CHATTING_UI, lpparam.classLoader,
                    "onConfigurationChanged", Configuration.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject instanceof Activity) {
                                    // Recreate the overlay with the new night/light colors.
                                    ChatGlassHook.attach((Activity) param.thisObject);
                                }
                            } catch (Throwable throwable) {
                                XposedBridge.log(TAG + " config refresh failed: " + throwable);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(CHATTING_UI, lpparam.classLoader, "onDestroy",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject instanceof Activity) {
                                    ChatGlassHook.detach((Activity) param.thisObject);
                                }
                            } catch (Throwable throwable) {
                                XposedBridge.log(TAG + " detach failed: " + throwable);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(LAUNCHER_UI, lpparam.classLoader, "onCreate",
                    Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject instanceof Activity) {
                                    Activity activity = (Activity) param.thisObject;
                                    HomeGlassHook.attach(activity);
                                }
                            } catch (Throwable throwable) {
                                XposedBridge.log(TAG + " home attach failed: " + throwable);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(LAUNCHER_UI, lpparam.classLoader, "onDestroy",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject instanceof Activity) {
                                    HomeGlassHook.detach((Activity) param.thisObject);
                                }
                            } catch (Throwable throwable) {
                                XposedBridge.log(TAG + " home detach failed: " + throwable);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(LAUNCHER_UI, lpparam.classLoader,
                    "onConfigurationChanged", Configuration.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject instanceof Activity) {
                                    Activity activity = (Activity) param.thisObject;
                                    HomeGlassHook.attach(activity);
                                }
                            } catch (Throwable throwable) {
                                XposedBridge.log(TAG + " home config failed: " + throwable);
                            }
                        }
                    });

            hookResume(CHATTING_UI, lpparam, true);
            hookResume(LAUNCHER_UI, lpparam, false);

            XposedBridge.log(TAG + " hooks installed for " + WECHAT);
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + " hook install failed: " + throwable);
        }
    }

    /**
     * WeChat's own target-35 fix pushes ActionBarContainer down by the status
     * bar height. That is exactly the "old bar" strip. Cancel the margin after
     * WeChat applies it.
     */
    private static int readInt(Object target, String name) {
        try {
            Object value = XposedHelpers.getObjectField(target, name);
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static void hookChatFooterPanel(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.tencent.mm.pluginsdk.ui.chat.ChatFooter",
                    lpparam.classLoader, "r1", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object footer = param.thisObject;
                                int x2 = readInt(footer, "x2");
                                if (x2 != 2) return;
                                View contextView = (View) footer;
                                float xdpi = contextView.getResources()
                                        .getDisplayMetrics().xdpi;
                                int reduce = Math.max(1,
                                        Math.round(0.15f / 2.54f * xdpi));
                                int v2 = readInt(footer, "V2");
                                int t2 = readInt(footer, "T2");
                                v2 = Math.max(1, v2 - reduce);
                                t2 = Math.max(1, t2 - reduce);
                                XposedHelpers.setIntField(footer, "V2", v2);
                                XposedHelpers.setIntField(footer, "T2", t2);
                                Object z = XposedHelpers.getObjectField(footer, "z");
                                if (z instanceof View) {
                                    ViewGroup.LayoutParams lp =
                                            ((View) z).getLayoutParams();
                                    if (lp != null) {
                                        lp.height = t2;
                                        ((View) z).setLayoutParams(lp);
                                    }
                                }
                                Object panel = XposedHelpers.getObjectField(footer, "h");
                                if (panel != null) {
                                    XposedHelpers.callMethod(panel, "setPortHeighPx", v2);
                                    XposedHelpers.callMethod(panel, "C");
                                }
                                XposedBridge.log(TAG + " panel r1 shrink reduce="
                                        + reduce + " V2=" + v2 + " T2=" + t2);
                            } catch (Throwable throwable) {
                                XposedBridge.log(TAG + " panel r1 failed: " + throwable);
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + " panel hook skipped: " + throwable);
        }
    }

    private static void hookHomeUiTopMargin(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("com.tencent.mm.ui.HomeUI",
                    lpparam.classLoader, "u", Activity.class, View.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                View view = (View) param.args[1];
                                if (view == null) {
                                    return;
                                }
                                if (view.getClass().getName().contains("ActionBarOverlayLayout")) {
                                    return;
                                }
                                ViewGroup.LayoutParams params = view.getLayoutParams();
                                if (params instanceof ViewGroup.MarginLayoutParams) {
                                    ViewGroup.MarginLayoutParams margins =
                                            (ViewGroup.MarginLayoutParams) params;
                                    android.util.Log.i("WeChatGlassEdge",
                                            "HomeUI.u before margin=" + margins.topMargin);
                                    if (margins.topMargin != 0) {
                                        margins.topMargin = 0;
                                        view.setLayoutParams(margins);
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + " HomeUI margin hook skipped: " + throwable);
        }
    }

    /**
     * FindMoreFriendsUI.N1() re-applies paddingTop=actionBarHeight every 100ms
     * on the 发现 page. This is UI-only and never touches payment/chat data.
     */
    private static void hookDiscoverTopPadding(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("com.tencent.mm.ui.FindMoreFriendsUI",
                    lpparam.classLoader, "N1", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object viewObject = XposedHelpers.callMethod(
                                        param.thisObject, "getView");
                                if (viewObject instanceof View) {
                                    View view = (View) viewObject;
                                    if (view.getPaddingTop() != 0) {
                                        view.setPadding(view.getPaddingLeft(), 0,
                                                view.getPaddingRight(), view.getPaddingBottom());
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + " discover padding hook skipped: " + throwable);
        }
    }

    private static void hookAppCompatOverlay(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("androidx.appcompat.widget.ActionBarOverlayLayout",
                    lpparam.classLoader, "onLayout",
                    boolean.class, int.class, int.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!(param.thisObject instanceof View)
                                        || !isLauncherContext(((View) param.thisObject)
                                        .getContext())) {
                                    return;
                                }
                                Object contentObject = XposedHelpers.getObjectField(
                                        param.thisObject, "mContent");
                                Object actionBarObject = XposedHelpers.getObjectField(
                                        param.thisObject, "mActionBarTop");
                                if (contentObject instanceof View) {
                                    View content = (View) contentObject;
                                    int top = content.getTop();
                                    if (top > 0) {
                                        content.setTranslationY(-top);
                                    }
                                    android.util.Log.i("WeChatGlassEdge",
                                            "overlay contentTop=" + top
                                                    + " ty=" + content.getTranslationY()
                                                    + " h=" + content.getHeight());
                                }
                                if (actionBarObject instanceof View) {
                                    ((View) actionBarObject).setAlpha(0f);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + " overlay layout hook skipped: " + throwable);
        }
    }

    private static boolean isLauncherContext(android.content.Context context) {
        while (context instanceof android.content.ContextWrapper) {
            if (context.getClass().getName().contains("LauncherUI")) {
                return true;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return context != null && context.getClass().getName().contains("LauncherUI");
    }

    private static void hookGlobalEdgeToEdge(final XC_LoadPackage.LoadPackageParam lpparam) {
        // Only the LauncherUI ActionBarOverlayLayout is touched. No global
        // Activity/PhoneWindow hook: payment and other plugin activities must
        // remain completely untouched.
        hookAppCompatOverlay(lpparam);
    }

    private static void hookExactBarVisibility(final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (((Integer) param.args[0]) != View.VISIBLE
                            || !(param.thisObject instanceof View)) {
                        return;
                    }
                    View view = (View) param.thisObject;
                    if (isLauncherContext(view.getContext())) {
                        param.args[0] = View.GONE;
                    }
                } catch (Throwable ignored) {
                }
            }
        };
        String[] classes = {
                "androidx.appcompat.widget.ActionBarContainer",
                "androidx.appcompat.widget.Toolbar",
                "androidx.appcompat.widget.ActionMenuView"
        };
        for (String className : classes) {
            try {
                XposedHelpers.findAndHookMethod(className, lpparam.classLoader,
                        "setVisibility", int.class, hook);
            } catch (Throwable throwable) {
                XposedBridge.log(TAG + " exact visibility hook skipped: " + className);
            }
        }
    }

    private static void hookActionBarOverlayDraw(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "androidx.appcompat.widget.ActionBarOverlayLayout",
                    lpparam.classLoader, "draw", android.graphics.Canvas.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject instanceof View
                                        && isLauncherContext(((View) param.thisObject)
                                        .getRootView().getContext())) {
                                    XposedHelpers.setObjectField(param.thisObject, "i", null);
                                    XposedHelpers.callMethod(param.thisObject,
                                            "setWillNotDraw", true);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + " overlay draw hook skipped: " + throwable);
        }
    }

    private static void hookStatusDraw(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.tencent.mm.ui.statusbar.DrawStatusBarFrameLayout",
                    lpparam.classLoader, "dispatchDraw",
                    android.graphics.Canvas.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject instanceof View
                                        && isLauncherContext(((View) param.thisObject)
                                        .getRootView().getContext())) {
                                    XposedHelpers.setIntField(param.thisObject, "h", 0);
                                    XposedHelpers.setIntField(param.thisObject, "i", 0);
                                    XposedHelpers.setIntField(param.thisObject, "m", 0);
                                    ((View) param.thisObject).setPadding(0, 0, 0, 0);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + " status draw hook skipped: " + throwable);
        }
    }

    private static void hookResume(final String className, XC_LoadPackage.LoadPackageParam lpparam,
                                   final boolean chat) {
        try {
            XposedHelpers.findAndHookMethod(className, lpparam.classLoader, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!(param.thisObject instanceof Activity)) {
                                    return;
                                }
                                Activity activity = (Activity) param.thisObject;
                                if (chat) {
                                    ChatGlassHook.reapplyStatusBar(activity);
                                } else {
                                    HomeGlassHook.reapplyStatusBar(activity);
                                }
                            } catch (Throwable throwable) {
                                XposedBridge.log(TAG + " resume status failed: " + throwable);
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + " resume hook skipped for " + className);
        }
    }
}
