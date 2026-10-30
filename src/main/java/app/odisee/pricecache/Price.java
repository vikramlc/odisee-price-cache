package app.odisee.pricecache;

import java.time.Instant;
import java.util.Objects;

/** A unit price loaded from the catalog service. */
public record Price(String sku, long cents, Instant loadedAt) {
    public Price {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(loadedAt, "loadedAt");
        if (cents < 0) {
            throw new IllegalArgumentException("cents must be >= 0");
        }
    }
}
