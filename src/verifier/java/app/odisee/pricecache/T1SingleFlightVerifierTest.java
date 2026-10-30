package app.odisee.pricecache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Hidden verifier for Task 1 (fail-to-pass). Do not read before solving. */
class T1SingleFlightVerifierTest {

    private static final int THREADS = 16;

    private final TestClock clock = new TestClock();
    private final AtomicInteger loads = new AtomicInteger();

    private PriceCache slowCache() {
        return new PriceCache(sku -> {
            loads.incrementAndGet();
            sleep(60);
            return new Price(sku, 42, clock.instant());
        }, clock, Duration.ofMinutes(5));
    }

    private List<Price> hammer(PriceCache cache, String sku) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Price>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return cache.get(sku);
            }));
        }
        start.countDown();
        List<Price> out = new ArrayList<>();
        for (Future<Price> f : futures) {
            out.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return out;
    }

    @Test
    void concurrentMissLoadsOnce() throws Exception {
        hammer(slowCache(), "HOT-1");
        assertEquals(1, loads.get());
    }

    @Test
    void allConcurrentCallersSeeTheSamePrice() throws Exception {
        List<Price> prices = hammer(slowCache(), "HOT-1");
        Set<Price> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(prices);
        assertEquals(1, distinct.size(), "callers received different Price instances");
    }

    @Test
    void concurrentReloadAfterExpiryLoadsOnce() throws Exception {
        PriceCache cache = slowCache();
        cache.get("HOT-1");
        clock.advance(Duration.ofMinutes(5));
        hammer(cache, "HOT-1");
        assertEquals(2, loads.get());
        assertTrue(cache.size() >= 1);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
