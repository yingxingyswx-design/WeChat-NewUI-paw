package com.wechatglass.liquidtab;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;

/**
 * Minimal legacy-Xposed bridge for the ported LiquidGlass hooks.
 *
 * <p>The upstream module uses libxposed API 102's {@code hook(...).intercept(...)}
 * chain. This shim exposes the same small surface ({@link Chain#getThisObject()},
 * {@link Chain#getArg(int)}, {@link Chain#getArgs()}, {@link Chain#proceed()})
 * on top of API 82's {@link XposedBridge#hookMethod}.
 */
final class LiquidGlassModule {

    interface Chain {
        Object getThisObject();

        Object getArg(int index);

        List<Object> getArgs();

        Object proceed() throws Throwable;
    }

    interface InterceptCallback {
        Object intercept(Chain chain) throws Throwable;
    }

    interface AfterCallback {
        void after(Chain chain) throws Throwable;
    }

    private LiquidGlassModule() {
    }

    static void hookAfter(Method method, AfterCallback callback) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    callback.after(new ChainImpl(param));
                } catch (Throwable t) {
                    LiquidTabLog.logErr("after-hook failed", t);
                }
            }
        });
    }

    static void hookIntercept(Method method, InterceptCallback callback) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    param.setResult(callback.intercept(new ChainImpl(param)));
                } catch (Throwable t) {
                    LiquidTabLog.logErr("intercept failed; running original", t);
                }
            }
        });
    }

    private static final class ChainImpl implements Chain {
        private final MethodHookParam param;

        ChainImpl(MethodHookParam param) {
            this.param = param;
        }

        @Override
        public Object getThisObject() {
            return param.thisObject;
        }

        @Override
        public Object getArg(int index) {
            Object[] args = param.args;
            return index >= 0 && index < args.length ? args[index] : null;
        }

        @Override
        public List<Object> getArgs() {
            return Arrays.asList(param.args);
        }

        @Override
        public Object proceed() throws Throwable {
            return XposedBridge.invokeOriginalMethod(
                    param.method, param.thisObject, param.args);
        }
    }
}
