package app.odisee.pricecache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceCacheLruTest {

    private final TestClock clock = new TestClock();
    private final List<String> loaded = new ArrayList<>();

    private PriceCache cache(int max) {
        return new PriceCache(sku -> {
            loaded.add(sku);
            return new Price(sku, 1, clock.instant());
        }, clock, Duration.ofMinutes(1), max);
    }

    @Test
    void evictsOldestWhenFull() {
        PriceCache cache = cache(2);
        cache.get("A");
        cache.get("B");
        cache.get("C");
        assertEquals(2, cache.size());
        cache.get("A");
        assertEquals(List.of("A", "B", "C", "A"), loaded);
    }

    @Test
    void hitRefreshesRecency() {
        PriceCache cache = cache(2);
        cache.get("A");
        cache.get("B");
        cache.get("A");
        cache.get("C"); // evicts B, not A
        cache.get("A");
        assertEquals(List.of("A", "B", "C"), loaded);
    }

    @Test
    void rejectsZeroMax() {
        assertThrows(IllegalArgumentException.class, () -> cache(0));
    }
}
