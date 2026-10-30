package app.odisee.pricecache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Read-through cache in front of the (slow) catalog price lookup.
 *
 * <p>Entries expire {@code ttl} after they were loaded. Used from many request
 * threads at once. With {@code maxEntries}, the least recently used entry is
 * evicted when the cache would grow past the limit.
 *
 * <p>Cost: {@code get} is O(1) (an access-ordered {@link LinkedHashMap}) plus the
 * loader on a miss. All operations hold the cache's monitor, so a slow load
 * blocks other callers; that is the trade-off for exact LRU order here.
 */
public final class PriceCache {

    private record Slot(Price price, Instant expiresAt) {}

    private final Function<String, Price> loader;
    private final Clock clock;
    private final Duration ttl;
    private final Map<String, Slot> entries;

    public PriceCache(Function<String, Price> loader, Clock clock, Duration ttl) {
        this(loader, clock, ttl, Integer.MAX_VALUE);
    }

    public PriceCache(Function<String, Price> loader, Clock clock, Duration ttl, int maxEntries) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Slot> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /** Returns the cached price, loading it when missing or expired. */
    public synchronized Price get(String sku) {
        Objects.requireNonNull(sku, "sku");
        Instant now = clock.instant();
        Slot entry = entries.get(sku); // access-ordered: a hit becomes most recent
        if (entry == null || !now.isBefore(entry.expiresAt())) {
            Price loaded = loader.apply(sku);
            entry = new Slot(loaded, now.plus(ttl));
            entries.put(sku, entry);
        }
        return entry.price();
    }

    public synchronized void invalidate(String sku) {
        entries.remove(sku);
    }

    public synchronized int size() {
        return entries.size();
    }
}
