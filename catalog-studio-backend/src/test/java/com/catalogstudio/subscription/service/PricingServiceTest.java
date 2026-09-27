package com.catalogstudio.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.catalogstudio.subscription.dto.PriceBreakdown;
import com.catalogstudio.subscription.entity.BillingPromoCode;
import com.catalogstudio.subscription.entity.BillingSettings;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.BillingPromoCodeRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private BillingPromoCodeRepository promoRepository;
    @Mock
    private BillingSettingsService billingSettingsService;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(promoRepository, billingSettingsService);
        BillingSettings billing = BillingSettings.builder()
                .serviceChargePercent(BigDecimal.ZERO)
                .gstPercent(new BigDecimal("18"))
                .companyLegalName("Catalog Studio")
                .companyGstin("")
                .parentCompanyName("Shirtaji")
                .build();
        when(billingSettingsService.current()).thenReturn(billing);
    }

    @Test
    void krishna10AppliesTenPercentOffPro() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .uuid(UUID.randomUUID())
                .name("PRO")
                .price(new BigDecimal("499.00"))
                .billingCycle("MONTHLY")
                .status("ACTIVE")
                .build();
        BillingPromoCode promo = BillingPromoCode.builder()
                .uuid(UUID.randomUUID())
                .code("KRISHNA10")
                .discountType("PERCENT")
                .discountValue(new BigDecimal("10"))
                .status("ACTIVE")
                .build();
        when(promoRepository.findByCodeIgnoreCase("KRISHNA10")).thenReturn(Optional.of(promo));

        PriceBreakdown quote = pricingService.quote(plan, "KRISHNA10");

        assertEquals(new BigDecimal("499.00"), quote.baseAmount());
        assertEquals(new BigDecimal("49.90"), quote.discountAmount());
        assertEquals(new BigDecimal("0.00"), quote.serviceCharge());
        // taxable 449.10 * 18% = 80.838 → 80.84
        assertEquals(new BigDecimal("80.84"), quote.gstAmount());
        assertEquals(new BigDecimal("529.94"), quote.totalAmount());
        assertEquals("KRISHNA10", quote.promoCode());
    }

    @Test
    void noPromoLeavesFullPlanPricePlusGst() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .uuid(UUID.randomUUID())
                .name("PRO")
                .price(new BigDecimal("499.00"))
                .billingCycle("MONTHLY")
                .status("ACTIVE")
                .build();

        PriceBreakdown quote = pricingService.quote(plan, null);

        assertEquals(new BigDecimal("499.00"), quote.baseAmount());
        assertEquals(new BigDecimal("0.00"), quote.discountAmount());
        assertEquals(new BigDecimal("89.82"), quote.gstAmount());
        assertEquals(new BigDecimal("588.82"), quote.totalAmount());
        assertNull(quote.promoCode());
    }
}
