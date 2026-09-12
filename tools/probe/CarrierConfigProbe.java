import android.os.IBinder;
import android.os.PersistableBundle;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads carrier config straight off the binder as the shell uid and compares it against the
 * values the module is supposed to inject. The expected table below is deliberately a separate
 * hardcoded copy of CarrierOverrides: two independent statements of the same intent are a test,
 * one shared constant would be a tautology.
 */
public class CarrierConfigProbe {

    private static final Map<String, Object> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("show_ims_registration_status_bool", true);
        EXPECTED.put("carrier_volte_available_bool", true);
        EXPECTED.put("carrier_vt_available_bool", true);
        EXPECTED.put("carrier_supports_ss_over_ut_bool", true);
        EXPECTED.put("carrier_cross_sim_ims_available_bool", true);
        EXPECTED.put("enable_cross_sim_calling_on_opportunistic_data_bool", true);
        EXPECTED.put("carrier_wfc_ims_available_bool", true);
        EXPECTED.put("carrier_wfc_supports_wifi_only_bool", true);
        EXPECTED.put("editable_wfc_mode_bool", true);
        EXPECTED.put("editable_wfc_roaming_mode_bool", true);
        EXPECTED.put("show_wifi_calling_icon_in_status_bar_bool", true);
        EXPECTED.put("wfc_spn_format_idx_int", 6);
        EXPECTED.put("editable_enhanced_4g_lte_bool", true);
        EXPECTED.put("hide_enhanced_4g_lte_bool", false);
        EXPECTED.put("hide_lte_plus_data_icon_bool", false);
        EXPECTED.put("vonr_enabled_bool", true);
        EXPECTED.put("vonr_setting_visibility_bool", true);
        EXPECTED.put("carrier_nr_availabilities_int_array", new int[]{1, 2});
        EXPECTED.put("5g_nr_ssrsrp_thresholds_int_array", new int[]{-128, -118, -108, -98});
    }

    public static void main(String[] args) throws Exception {
        int subId = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "carrier_config");
        if (binder == null) {
            System.out.println("FATAL carrier_config service not found");
            System.exit(2);
        }

        Class<?> stub = Class.forName("com.android.internal.telephony.ICarrierConfigLoader$Stub");
        Object loader = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);

        Method reader = null;
        for (String name : new String[]{"getConfigForSubIdWithFeature", "getConfigForSubId"}) {
            for (Method m : loader.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getReturnType() == PersistableBundle.class
                        && (reader == null || m.getParameterCount() < reader.getParameterCount())) {
                    reader = m;
                }
            }
            if (reader != null) {
                break;
            }
        }
        if (reader == null) {
            System.out.println("FATAL no reader method on " + loader.getClass());
            System.exit(2);
        }

        Object[] callArgs = new Object[reader.getParameterCount()];
        callArgs[0] = subId;
        for (int i = 1; i < callArgs.length; i++) {
            callArgs[i] = i == 1 ? "com.android.shell" : null;
        }

        System.out.println("reader   " + reader.getName() + "/" + reader.getParameterCount()
                + "  subId=" + subId);

        PersistableBundle config = (PersistableBundle) reader.invoke(loader, callArgs);
        if (config == null) {
            System.out.println("FATAL reader returned null");
            System.exit(2);
        }
        System.out.println("keys     " + config.size());

        int failed = 0;
        for (Map.Entry<String, Object> e : EXPECTED.entrySet()) {
            String key = e.getKey();
            Object want = e.getValue();
            boolean ok;
            String got;
            if (want instanceof int[]) {
                int[] actual = config.getIntArray(key);
                ok = Arrays.equals((int[]) want, actual);
                got = actual == null ? "null" : Arrays.toString(actual);
                want = Arrays.toString((int[]) want);
            } else if (want instanceof Boolean) {
                boolean actual = config.getBoolean(key);
                ok = want.equals(actual);
                got = String.valueOf(actual);
            } else {
                int actual = config.getInt(key);
                ok = want.equals(actual);
                got = String.valueOf(actual);
            }
            System.out.printf("%-4s %-52s want=%-24s got=%s%n", ok ? "PASS" : "FAIL", key, want, got);
            if (!ok) {
                failed++;
            }
        }

        System.out.println(failed == 0
                ? "RESULT all " + EXPECTED.size() + " keys match"
                : "RESULT " + failed + " of " + EXPECTED.size() + " keys mismatch");
        System.exit(failed == 0 ? 0 : 1);
    }
}
