package io.github.nku100.carrierims;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class HookTargets {

    private HookTargets() {
    }

    /**
     * 按名字与返回类型定位方法，不约束参数列表——Android 每隔几个版本就给这些私有方法加参数，
     * 写死签名等于给自己埋一颗定时炸弹。
     * 同名同返回类型有多个重载时取参数最少的那个，只为让结果可预测：
     * {@link Class#getDeclaredMethods()} 不保证顺序。
     */
    static Method find(Class<?> owner, Class<?> returnType, String... namesByPriority) {
        for (String name : namesByPriority) {
            Method best = null;
            for (Method method : owner.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (!name.equals(method.getName()) || method.getReturnType() != returnType) {
                    continue;
                }
                if (best == null || method.getParameterCount() < best.getParameterCount()) {
                    best = method;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    static String describe(Class<?> owner) {
        StringBuilder sb = new StringBuilder(owner.getName()).append(" declares:");
        for (Method method : owner.getDeclaredMethods()) {
            sb.append("\n  ").append(method.getReturnType().getSimpleName())
                    .append(' ').append(method.getName()).append('(');
            Class<?>[] params = method.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(params[i].getSimpleName());
            }
            sb.append(')');
        }
        return sb.toString();
    }
}
