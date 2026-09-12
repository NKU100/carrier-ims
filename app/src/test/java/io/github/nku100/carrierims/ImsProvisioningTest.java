package io.github.nku100.carrierims;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public class ImsProvisioningTest {

    private static final Executor DIRECT = Runnable::run;

    private static IntConsumer failing(List<Integer> attempts) {
        return subId -> {
            attempts.add(subId);
            throw new IllegalStateException("ims not ready");
        };
    }

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
    public void retriesUntilTheWriteSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        List<Integer> succeeded = new ArrayList<>();
        IntConsumer flaky = subId -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("ims not ready");
            }
            succeeded.add(subId);
        };
        ImsProvisioning p = new ImsProvisioning(DIRECT, flaky);

        for (int i = 0; i < 5; i++) {
            p.ensureEnabled(1);
        }

        assertEquals(3, calls.get());
        assertEquals(List.of(1), succeeded);
    }

    @Test
    public void stopsRetryingAfterTenFailures() {
        List<Integer> attempts = new ArrayList<>();
        ImsProvisioning p = new ImsProvisioning(DIRECT, failing(attempts));

        for (int i = 0; i < 50; i++) {
            p.ensureEnabled(1);
        }

        assertEquals(10, attempts.size());
    }

    @Test
    public void failuresOnOneSubIdDoNotBlockAnother() {
        List<Integer> attempts = new ArrayList<>();
        ImsProvisioning p = new ImsProvisioning(DIRECT, failing(attempts));

        p.ensureEnabled(1);
        p.ensureEnabled(2);

        assertEquals(List.of(1, 2), attempts);
    }
}
