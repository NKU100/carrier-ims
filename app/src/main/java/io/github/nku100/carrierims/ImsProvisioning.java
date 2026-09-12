package io.github.nku100.carrierims;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * 部分运营商在 carrier config 之外的第二层拦住 VoLTE：provisioning 标志 VoIMS opt-in 仍是 disabled。
 * carrier config 的读取 hook 覆盖不到这一层，所以这里补上。
 */
final class ImsProvisioning {

    private final Set<Integer> handled = ConcurrentHashMap.newKeySet();
    private final Executor executor;
    private final IntConsumer writer;

    ImsProvisioning(Executor executor, IntConsumer writer) {
        this.executor = executor;
        this.writer = writer;
    }

    static ImsProvisioning createDefault() {
        return new ImsProvisioning(Executors.newSingleThreadExecutor(),
                ImsProvisioning::writeVoImsOptIn);
    }

    /**
     * 按 subId 而不是全局标志去重，换卡后新的 subId 依然会被处理。
     * 失败后不重试：调用点是全系统最热的读取路径之一，一个长期失败的写入会把日志刷爆。
     */
    void ensureEnabled(int subId) {
        if (subId < 0 || !handled.add(subId)) {
            return;
        }
        executor.execute(() -> {
            try {
                writer.accept(subId);
            } catch (Throwable t) {
                Log.w(CarrierImsModule.TAG, "VoIMS opt-in failed for subId=" + subId, t);
            }
        });
    }

    /**
     * ProvisioningManager 的这几个成员都是 @SystemApi，public SDK 里没有，只能反射。
     * 常量值也从字段读取，而不是把 68 / 1 写死——数值是框架内部约定，硬编码会静默失效。
     * com.android.phone 是平台签名系统应用，不受 hidden API 限制，且持有 MODIFY_PHONE_STATE。
     */
    static void writeVoImsOptIn(int subId) {
        try {
            Class<?> pm = Class.forName("android.telephony.ims.ProvisioningManager");
            int key = pm.getField("KEY_VOIMS_OPT_IN_STATUS").getInt(null);
            int enabled = pm.getField("PROVISIONING_VALUE_ENABLED").getInt(null);

            Object manager = pm.getMethod("createForSubscriptionId", int.class)
                    .invoke(null, subId);

            Method get = pm.getMethod("getProvisioningIntValue", int.class);
            int current = (Integer) get.invoke(manager, key);
            if (current == enabled) {
                Log.i(CarrierImsModule.TAG, "VoIMS opt-in already enabled for subId=" + subId);
                return;
            }

            Method set = pm.getMethod("setProvisioningIntValue", int.class, int.class);
            Object result = set.invoke(manager, key, enabled);
            Log.i(CarrierImsModule.TAG,
                    "VoIMS opt-in set for subId=" + subId + " was=" + current + " result=" + result);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ProvisioningManager reflection failed", e);
        }
    }
}
