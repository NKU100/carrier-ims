package io.github.nku100.carrierims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Method;

public class HookTargetsTest {

    @SuppressWarnings("unused")
    static class Loader {
        public String preferred(int subId, String pkg, String feature) {
            return "preferred/3";
        }

        public String preferred(int subId) {
            return "preferred/1";
        }

        public void preferred(long ignored) {
        }

        public String fallback(int subId, String pkg) {
            return "fallback/2";
        }

        public int unrelated() {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    static class OnlyFallback {
        public String fallback(int subId) {
            return "fallback/1";
        }
    }

    @SuppressWarnings("unused")
    static class Nothing {
        public int preferred(int subId) {
            return 0;
        }
    }

    @Test
    public void picksFirstNameInPriorityOrder() {
        Method m = HookTargets.find(Loader.class, String.class, "preferred", "fallback");
        assertEquals("preferred", m.getName());
    }

    @Test
    public void amongOverloadsPicksFewestParameters() {
        Method m = HookTargets.find(Loader.class, String.class, "preferred");
        assertEquals(1, m.getParameterCount());
    }

    @Test
    public void ignoresSameNameWithDifferentReturnType() {
        assertNull(HookTargets.find(Nothing.class, String.class, "preferred"));
    }

    @Test
    public void fallsBackToLaterName() {
        Method m = HookTargets.find(OnlyFallback.class, String.class, "preferred", "fallback");
        assertEquals("fallback", m.getName());
    }

    @Test
    public void returnsNullWhenNothingMatches() {
        assertNull(HookTargets.find(Nothing.class, Long.class, "preferred", "fallback"));
    }

    @Test
    public void describeListsDeclaredMethods() {
        String text = HookTargets.describe(OnlyFallback.class);
        assertTrue(text, text.contains("OnlyFallback"));
        assertTrue(text, text.contains("String fallback(int)"));
    }
}
