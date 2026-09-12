package io.github.nku100.carrierims;

import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;

final class CarrierOverrides {

    // 这两个 key 在 CarrierConfigManager 里是 @hide，public SDK 没有常量。
    // carrier config 的 key 本身就是字符串，内联字面量即可。
    private static final String KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL =
            "show_wifi_calling_icon_in_status_bar_bool";
    private static final String KEY_WFC_SPN_FORMAT_IDX_INT = "wfc_spn_format_idx_int";

    private static final PersistableBundle OVERRIDES = build();

    private CarrierOverrides() {
    }

    /** 返回的 bundle 是共享的，调用方只能把它 putAll 进别处，绝不能改它。 */
    static PersistableBundle get() {
        return OVERRIDES;
    }

    private static PersistableBundle build() {
        PersistableBundle b = new PersistableBundle();

        b.putBoolean(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL, true);

        b.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
        b.putBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true);
        b.putBoolean(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL, true);

        b.putBoolean(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL, true);
        b.putBoolean(CarrierConfigManager
                .KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL, true);

        b.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true);
        b.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true);
        b.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true);
        b.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, true);
        b.putBoolean(KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL, true);
        b.putInt(KEY_WFC_SPN_FORMAT_IDX_INT, 6);

        b.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true);
        b.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false);
        b.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false);

        b.putBoolean(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true);
        b.putBoolean(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true);
        b.putIntArray(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY, new int[]{
                CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA,
        });
        b.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                new int[]{-128, -118, -108, -98});

        return b;
    }
}
