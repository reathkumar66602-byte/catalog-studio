package com.catalogstudio.subscription.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.subscription.dto.PriceBreakdown;
import com.catalogstudio.subscription.entity.BillingPromoCode;
import com.catalogstudio.subscription.entity.BillingSettings;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.BillingPromoCodeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PricingService {

    private static final RoundingMode MONEY = RoundingMode.HALF_UP;

    private final BillingPromoCodeRepository promoRepository;
    private final BillingSettingsService billingSettingsService;

    @Transactional(readOnly = true)
    public PriceBreakdown quote(SubscriptionPlan plan, String promoCode) {
        BillingSettings billing = billingSettingsService.current();
        BigDecimal base = money(plan.getPrice());
        BillingPromoCode promo = resolvePromo(promoCode);
        BigDecimal discount = discountFor(base, promo);
        if (discount.compareTo(base) > 0) {
            discount = base;
        }
        BigDecimal afterDiscount = base.subtract(discount);
        BigDecimal servicePct = money(billing.getServiceChargePercent());
        BigDecimal gstPct = money(billing.getGstPercent());
        BigDecimal serviceCharge = afterDiscount
                .multiply(servicePct)
                .divide(BigDecimal.valueOf(100), 2, MONEY);
        BigDecimal taxable = afterDiscount.add(serviceCharge);
        BigDecimal gst = taxable
                .multiply(gstPct)
                .divide(BigDecimal.valueOf(100), 2, MONEY);
        BigDecimal total = taxable.add(gst);
        String label = null;
        String code = null;
        if (promo != null) {
            code = promo.getCode();
            label = "PERCENT".equalsIgnoreCase(promo.getDiscountType())
                    ? promo.getDiscountValue().stripTrailingZeros().toPlainString() + "% off"
                    : "₹" + promo.getDiscountValue().stripTrailingZeros().toPlainString() + " off";
        }
        return new PriceBreakdown(
                base,
                discount,
                serviceCharge,
                gst,
                total,
                servicePct,
                gstPct,
                code,
                label,
                billing.getCompanyLegalName(),
                billing.getCompanyGstin(),
                billing.getParentCompanyName()
        );
    }

    @Transactional
    public void consumePromo(String promoCode) {
        if (!StringUtils.hasText(promoCode)) {
            return;
        }
        BillingPromoCode promo = resolvePromo(promoCode);
        if (promo == null) {
            return;
        }
        promo.setUsedCount(promo.getUsedCount() + 1);
        if (promo.getMaxUses() != null && promo.getUsedCount() >= promo.getMaxUses()) {
            promo.setStatus("DISABLED");
        }
    }

    private BillingPromoCode resolvePromo(String promoCode) {
        if (!StringUtils.hasText(promoCode)) {
            return null;
        }
        BillingPromoCode promo = promoRepository.findByCodeIgnoreCase(promoCode.trim())
                .orElseThrow(() -> ApiException.badRequest("Promo code is not valid"));
        if (!promo.isUsableToday()) {
            throw ApiException.badRequest("This promo code is expired or no longer available");
        }
        return promo;
    }

    private BigDecimal discountFor(BigDecimal base, BillingPromoCode promo) {
        if (promo == null || promo.getDiscountValue() == null) {
            return BigDecimal.ZERO.setScale(2, MONEY);
        }
        if ("FIXED".equalsIgnoreCase(promo.getDiscountType())) {
            return money(promo.getDiscountValue());
        }
        return base.multiply(money(promo.getDiscountValue()))
                .divide(BigDecimal.valueOf(100), 2, MONEY);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, MONEY);
        }
        return value.setScale(2, MONEY);
    }
}
