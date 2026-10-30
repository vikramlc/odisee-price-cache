package app.odisee.pricecache;

/** Quotes order lines from cached unit prices. */
public final class PricingService {

    static final int BULK_QUANTITY = 10;
    static final int BULK_DISCOUNT_PERCENT = 5;

    private final PriceCache cache;

    public PricingService(PriceCache cache) {
        this.cache = cache;
    }

    /** Total in cents for {@code quantity} units; 5% off from 10 units, rounded down. */
    public long quote(String sku, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
        long total = cache.get(sku).cents() * quantity;
        if (quantity >= BULK_QUANTITY) {
            total = total * (100 - BULK_DISCOUNT_PERCENT) / 100;
        }
        return total;
    }
}
