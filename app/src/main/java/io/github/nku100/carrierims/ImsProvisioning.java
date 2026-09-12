package io.github.nku100.carrierims;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.Map;
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

    /**
     * com.android.phone 刚起来时 IMS 栈还没绑好，头几次写入必然失败（实测返回 CONFIG_RESULT_FAILED）。
     * 重试由后续的 carrier config 读取驱动，读取本就频繁，不需要自己排期；设上限只是防止
     * 一个永久失败的写入把日志刷爆——调用点是全系统最热的路径之一。
     */
    private static final int MAX_ATTEMPTS = 10;

    private final Set<Integer> settled = ConcurrentHashMap.newKeySet();
    private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> attempts = new ConcurrentHashMap<>();
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

    /** 按 subId 而不是全局标志去重，换卡后新的 subId 依然会被处理。 */
    void ensureEnabled(int subId) {
        if (subId < 0 || settled.contains(subId) || !inFlight.add(subId)) {
            return;
        }
        executor.execute(() -> {
            try {
                writer.accept(subId);
                settled.add(subId);
                Log.i(CarrierImsModule.TAG, "VoIMS opt-in enabled for subId=" + subId);
            } catch (Throwable t) {
                int attempt = attempts.merge(subId, 1, Integer::sum);
                if (attempt >= MAX_ATTEMPTS) {
                    settled.add(subId);
                    Log.w(CarrierImsModule.TAG, "VoIMS opt-in gave up for subId=" + subId
                            + " after " + attempt + " attempts", t);
                } else {
                    Log.i(CarrierImsModule.TAG, "VoIMS opt-in attempt " + attempt
                            + " failed for subId=" + subId + ": " + t);
                }
            } finally {
                inFlight.remove(subId);
            }
        });
    }

    /**
     * ProvisioningManager 的这几个成员都是 @SystemApi，public SDK 里没有，只能反射。
     * 常量值也从字段读取，而不是把 68 / 1 写死——数值是框架内部约定，硬编码会静默失效。
     * com.android.phone 是平台签名系统应用，不受 hidden API 限制，且持有 MODIFY_PHONE_STATE。
     * <p>
     * 成功与否以回读为准，不看 setProvisioningIntValue 的返回码：返回码的取值定义在 @hide 的
     * ImsConfigImplBase 里，而回读直接检验我们真正关心的那件事。
     */
    static void writeVoImsOptIn(int subId) {
        try {
            Class<?> pm = Class.forName("android.telephony.ims.ProvisioningManager");
            int key = pm.getField("KEY_VOIMS_OPT_IN_STATUS").getInt(null);
            int enabled = pm.getField("PROVISIONING_VALUE_ENABLED").getInt(null);

            Object manager = pm.getMethod("createForSubscriptionId", int.class)
                    .invoke(null, subId);
            Method get = pm.getMethod("getProvisioningIntValue", int.class);

            if ((Integer) get.invoke(manager, key) == enabled) {
                return;
            }

            pm.getMethod("setProvisioningIntValue", int.class, int.class)
                    .invoke(manager, key, enabled);

            int after = (Integer) get.invoke(manager, key);
            if (after != enabled) {
                throw new IllegalStateException("VoIMS opt-in still " + after);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ProvisioningManager reflection failed", e);
        }
    }
}
