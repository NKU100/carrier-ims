package io.github.nku100.carrierims;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

public class CarrierImsModule extends XposedModule {

    static final String TAG = "CarrierIms";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        Log.i(TAG, "onPackageReady " + param.getPackageName());
    }
}
