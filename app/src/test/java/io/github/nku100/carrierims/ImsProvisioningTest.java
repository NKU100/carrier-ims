package io.github.nku100.carrierims;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;

public class ImsProvisioningTest {

    private static final Executor DIRECT = Runnable::run;

    @Test
    public void writesOncePerSubId() {
        List<Integer> written = new ArrayList<>();
        ImsProvisioning p = new ImsProvisioning(DIRECT, written::add);

        p.ensureEnabled(1);
        p.ensureEnabled(1);
        p.ensureEnabled(1);

        assertEquals(List.of(1), written);
    }

    @Test
    public void writesForEachDistinctSubId() {
        List<Integer> written = new ArrayList<>();
        ImsProvisioning p = new ImsProvisioning(DIRECT, written::add);

        p.ensureEnabled(1);
        p.ensureEnabled(2);
        p.ensureEnabled(1);

        assertEquals(List.of(1, 2), written);
    }

    @Test
    public void ignoresInvalidSubId() {
        List<Integer> written = new ArrayList<>();
        ImsProvisioning p = new ImsProvisioning(DIRECT, written::add);

        p.ensureEnabled(-1);

        assertEquals(List.of(), written);
    }

    @Test
    public void swallowsWriterFailure() {
        IntConsumer boom = subId -> {
            throw new IllegalStateException("boom");
        };

        new ImsProvisioning(DIRECT, boom).ensureEnabled(1);
    }

    @Test
    public void doesNotRetryAfterFailure() {
        List<Integer> attempts = new ArrayList<>();
        IntConsumer boom = subId -> {
            attempts.add(subId);
            throw new IllegalStateException("boom");
        };
        ImsProvisioning p = new ImsProvisioning(DIRECT, boom);

        p.ensureEnabled(1);
        p.ensureEnabled(1);

        assertEquals(List.of(1), attempts);
    }
}
