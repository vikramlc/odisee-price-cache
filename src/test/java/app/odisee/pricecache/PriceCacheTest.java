package app.odisee.pricecache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PriceCacheTest {

    private final TestClock clock = new TestClock();
    private final AtomicInteger loads = new AtomicInteger();

    private PriceCache cache(Duration ttl) {
        return new PriceCache(sku -> {
            loads.incrementAndGet();
            return new Price(sku, 100L * sku.length(), clock.instant());
        }, clock, ttl);
    }

    @Test
    void secondGetIsAHit() {
        PriceCache cache = cache(Duration.ofMinutes(5));
        Price first = cache.get("SKU-1");
        assertSame(first, cache.get("SKU-1"));
        assertEquals(1, loads.get());
    }

    @Test
    void expiredEntryIsReloaded() {
        PriceCache cache = cache(Duration.ofMinutes(5));
        cache.get("SKU-1");
        clock.advance(Duration.ofMinutes(5));
        cache.get("SKU-1");
        assertEquals(2, loads.get());
    }

    @Test
    void entryJustBeforeExpiryIsAHit() {
        PriceCache cache = cache(Duration.ofMinutes(5));
        cache.get("SKU-1");
        clock.advance(Duration.ofMinutes(5).minusMillis(1));
        cache.get("SKU-1");
        assertEquals(1, loads.get());
    }

    @Test
    void invalidateForcesReload() {
        PriceCache cache = cache(Duration.ofMinutes(5));
        cache.get("SKU-1");
        cache.invalidate("SKU-1");
        cache.get("SKU-1");
        assertEquals(2, loads.get());
    }

    @Test
    void keysAreIndependent() {
        PriceCache cache = cache(Duration.ofMinutes(5));
        cache.get("A");
        cache.get("BB");
        assertEquals(2, cache.size());
        assertEquals(200L, cache.get("BB").cents());
    }

    @Test
    void loaderFailurePropagatesAndIsNotCached() {
        AtomicInteger calls = new AtomicInteger();
        PriceCache cache = new PriceCache(sku -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("catalog down");
            }
            return new Price(sku, 5, clock.instant());
        }, clock, Duration.ofMinutes(1));
        assertThrows(IllegalStateException.class, () -> cache.get("X"));
        assertEquals(5L, cache.get("X").cents());
    }

    @Test
    void rejectsNullSkuAndBadTtl() {
        PriceCache cache = cache(Duration.ofMinutes(5));
        assertThrows(NullPointerException.class, () -> cache.get(null));
        assertThrows(IllegalArgumentException.class, () -> cache(Duration.ZERO));
    }
}
