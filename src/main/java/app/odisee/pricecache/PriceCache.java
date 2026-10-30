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

    /**
     * Returns the cached price, loading it when missing or expired.
     *
     * <p>Fresh hits are a lock-free read. A miss or an expired entry is loaded inside
     * {@link ConcurrentHashMap#compute}, which runs at most one remapping per key at a
     * time, so concurrent callers for the same SKU wait for one load and share its
     * result. A loader exception propagates and leaves the mapping unchanged.
     */
    public Price get(String sku) {
        Objects.requireNonNull(sku, "sku");
        Entry cached = entries.get(sku);
        if (cached != null && clock.instant().isBefore(cached.expiresAt())) {
            return cached.price();
        }
        Entry entry = entries.compute(sku, (key, current) -> {
            Instant now = clock.instant();
            if (current != null && now.isBefore(current.expiresAt())) {
                return current; // another thread loaded it while we waited
            }
            return new Entry(loader.apply(key), now.plus(ttl));
        });
        return entry.price();
    }

    public void invalidate(String sku) {
        entries.remove(sku);
    }

    public int size() {
        return entries.size();
    }
}
