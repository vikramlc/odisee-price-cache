package app.odisee.pricecache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final TestClock clock = new TestClock();
    private final PricingService service = new PricingService(
            new PriceCache(sku -> new Price(sku, 250, clock.instant()), clock, Duration.ofMinutes(1)));

    @Test
    void quotesUnitPriceTimesQuantity() {
        assertEquals(750, service.quote("SKU-1", 3));
    }

    @Test
    void appliesBulkDiscountFromTenUnits() {
        assertEquals(2375, service.quote("SKU-1", 10));
    }

    @Test
    void rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () -> service.quote("SKU-1", 0));
    }
}
