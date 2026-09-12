package io.github.nku100.carrierims;

import android.os.PersistableBundle;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

import java.lang.reflect.Method;

public class CarrierImsModule extends XposedModule {

    static final String TAG = "CarrierIms";

    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String LOADER_CLASS = "com.android.phone.CarrierConfigLoader";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!PHONE_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        Class<?> loader;
        try {
            loader = Class.forName(LOADER_CLASS, false, param.getClassLoader());
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "class not found: " + LOADER_CLASS, e);
            return;
        }

        // getConfigForSubId 与 getConfigSubsetForSubIdWithFeature 都委托给带 Feature 的这个方法，
        // 所以只 hook 它一处就覆盖全部读取入口；多 hook 只会重复注入。
        Method target = HookTargets.find(loader, PersistableBundle.class,
                "getConfigForSubIdWithFeature", "getConfigForSubId");
        if (target == null) {
            Log.e(TAG, "no carrier config reader found. " + HookTargets.describe(loader));
            return;
        }

        ImsProvisioning provisioning = ImsProvisioning.createDefault();

        hook(target)
                // 高优先级的拦截器位于链首，包住其余环节，因此它在 proceed() 之后所做的修改最后生效。
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                // 本方法是全系统都在调的路径，拦截器抛异常足以打挂 telephony。
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    int subId = subIdOf(chain);
                    Object result = chain.proceed();
                    if (result instanceof PersistableBundle) {
                        try {
                            ((PersistableBundle) result).putAll(CarrierOverrides.get());
                        } catch (Throwable t) {
                            Log.w(TAG, "failed to merge overrides", t);
                        }
                    }
                    if (subId >= 0) {
                        provisioning.ensureEnabled(subId);
                    }
                    return result;
                });

        Log.i(TAG, "hooked " + target);
    }

    private static int subIdOf(XposedInterface.Chain chain) {
        try {
            Object arg = chain.getArg(0);
            return arg instanceof Integer ? (Integer) arg : -1;
        } catch (Throwable t) {
            return -1;
        }
    }
}
