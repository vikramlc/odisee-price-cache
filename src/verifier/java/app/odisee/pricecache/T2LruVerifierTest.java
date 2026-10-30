package app.odisee.pricecache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Hidden verifier for Task 2 (fail-to-pass). Do not read before solving. */
class T2LruVerifierTest {

    private final TestClock clock = new TestClock();
    private final List<String> loaded = new ArrayList<>();

    private PriceCache cache(int maxEntries) {
        return new PriceCache(sku -> {
            loaded.add(sku);
            return new Price(sku, 10, clock.instant());
        }, clock, Duration.ofMinutes(5), maxEntries);
    }

    @Test
    void evictsLeastRecentlyUsed() {
        PriceCache cache = cache(2);
        cache.get("A");
        cache.get("B");
        cache.get("C"); // evicts A
        loaded.clear();
        cache.get("B");
        cache.get("C");
        assertEquals(List.of(), loaded);
        cache.get("A");
        assertEquals(List.of("A"), loaded);
    }

    @Test
    void getRefreshesRecency() {
        PriceCache cache = cache(2);
        cache.get("A");
        cache.get("B");
        cache.get("A"); // A is now most recent
        cache.get("C"); // evicts B
        loaded.clear();
        cache.get("A");
        assertEquals(List.of(), loaded);
        cache.get("B");
        assertEquals(List.of("B"), loaded);
    }

    @Test
    void sizeNeverExceedsMax() {
        PriceCache cache = cache(3);
        for (int i = 0; i < 50; i++) {
            cache.get("SKU-" + i);
            assertTrue(cache.size() <= 3);
        }
        assertEquals(3, cache.size());
    }

    @Test
    void rejectsMaxBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> cache(0));
    }

    @Test
    void ttlStillAppliesWithLimit() {
        PriceCache cache = cache(2);
        cache.get("A");
        clock.advance(Duration.ofMinutes(5));
        cache.get("A");
        assertEquals(List.of("A", "A"), loaded);
    }
}
