package app.odisee.pricecache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PriceCacheConcurrencyTest {

    @Test
    void concurrentMissesShareOneLoad() throws Exception {
        TestClock clock = new TestClock();
        AtomicInteger loads = new AtomicInteger();
        PriceCache cache = new PriceCache(sku -> {
            loads.incrementAndGet();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new Price(sku, 1, clock.instant());
        }, clock, Duration.ofMinutes(1));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 8; i++) {
            pool.submit(() -> {
                start.await();
                return cache.get("HOT");
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        assertEquals(1, loads.get());
    }
}
