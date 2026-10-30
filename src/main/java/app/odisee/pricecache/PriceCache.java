package app.odisee.pricecache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Read-through cache in front of the (slow) catalog price lookup.
 *
 * <p>Entries expire {@code ttl} after they were loaded. Used from many request
 * threads at once.
 */
public final class PriceCache {

    private record Entry(Price price, Instant expiresAt) {}

    private final Function<String, Price> loader;
    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public PriceCache(Function<String, Price> loader, Clock clock, Duration ttl) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    /** Returns the cached price, loading it when missing or expired. */
    public Price get(String sku) {
        Objects.requireNonNull(sku, "sku");
        Instant now = clock.instant();
        Entry entry = entries.get(sku);
        if (entry == null || !now.isBefore(entry.expiresAt())) {
            Price loaded = loader.apply(sku);
            entry = new Entry(loaded, now.plus(ttl));
            entries.put(sku, entry);
        }
        return entry.price();
    }

    public void invalidate(String sku) {
        entries.remove(sku);
    }

    public int size() {
        return entries.size();
    }
}
