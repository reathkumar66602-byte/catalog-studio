package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.wholesale.dto.CouponCreateRequest;
import com.catalogstudio.wholesale.dto.CouponResponse;
import com.catalogstudio.wholesale.dto.CouponUpdateRequest;
import com.catalogstudio.wholesale.entity.WholesaleCoupon;
import com.catalogstudio.wholesale.entity.WholesaleCouponRedemption;
import com.catalogstudio.wholesale.entity.WholesaleStore;
import com.catalogstudio.wholesale.repository.WholesaleCouponRedemptionRepository;
import com.catalogstudio.wholesale.repository.WholesaleCouponRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WholesaleCouponService {

    private static final RoundingMode MONEY = RoundingMode.HALF_UP;

    private final WholesaleAccessService accessService;
    private final WholesaleStoreRepository storeRepository;
    private final WholesaleCouponRepository couponRepository;
    private final WholesaleCouponRedemptionRepository redemptionRepository;

    // --- supplier CRUD --------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CouponResponse> list(Long supplierUserId) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        return couponRepository.findByStoreIdOrderByCreatedAtDesc(store.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CouponResponse create(Long supplierUserId, CouponCreateRequest request) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        String code = normalizeCode(request.code());
        if (couponRepository.existsByStoreIdAndCodeIgnoreCase(store.getId(), code)) {
            throw ApiException.conflict("A coupon with this code already exists");
        }
        String type = normalizeType(request.discountType());
        validateDiscount(type, request.discountValue(), request.maxDiscount());
        validateWindow(request.startsAt(), request.endsAt());

        WholesaleCoupon coupon = WholesaleCoupon.builder()
                .storeId(store.getId())
                .code(code)
                .discountType(type)
                .discountValue(money(request.discountValue()))
                .minOrder(request.minOrder() == null ? BigDecimal.ZERO.setScale(2, MONEY) : money(request.minOrder()))
                .maxDiscount(request.maxDiscount() == null ? null : money(request.maxDiscount()))
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .usageLimit(request.usageLimit())
                .active(request.active() == null || request.active())
                .build();
        try {
            coupon = couponRepository.saveAndFlush(coupon);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("A coupon with this code already exists");
        }
        return toResponse(coupon);
    }

    @Transactional
    public CouponResponse update(Long supplierUserId, UUID couponId, CouponUpdateRequest request) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        WholesaleCoupon coupon = couponRepository.findByStoreIdAndUuid(store.getId(), couponId)
                .orElseThrow(() -> ApiException.notFound("Coupon not found"));

        if (StringUtils.hasText(request.code())) {
            String code = normalizeCode(request.code());
            Long couponPk = coupon.getId();
            couponRepository.findByStoreIdAndCodeIgnoreCase(store.getId(), code)
                    .filter(existing -> !existing.getId().equals(couponPk))
                    .ifPresent(existing -> {
                        throw ApiException.conflict("A coupon with this code already exists");
                    });
            coupon.setCode(code);
        }
        if (StringUtils.hasText(request.discountType())) {
            coupon.setDiscountType(normalizeType(request.discountType()));
        }
        if (request.discountValue() != null) {
            coupon.setDiscountValue(money(request.discountValue()));
        }
        if (request.minOrder() != null) {
            coupon.setMinOrder(money(request.minOrder()));
        }
        if (request.maxDiscount() != null) {
            coupon.setMaxDiscount(money(request.maxDiscount()));
        }
        if (request.startsAt() != null) {
            coupon.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            coupon.setEndsAt(request.endsAt());
        }
        if (request.usageLimit() != null) {
            coupon.setUsageLimit(request.usageLimit());
        }
        if (request.active() != null) {
            coupon.setActive(request.active());
        }
        validateDiscount(coupon.getDiscountType(), coupon.getDiscountValue(), coupon.getMaxDiscount());
        validateWindow(coupon.getStartsAt(), coupon.getEndsAt());
        try {
            coupon = couponRepository.saveAndFlush(coupon);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("A coupon with this code already exists");
        }
        return toResponse(coupon);
    }

    @Transactional
    public void delete(Long supplierUserId, UUID couponId) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        WholesaleCoupon coupon = couponRepository.findByStoreIdAndUuid(store.getId(), couponId)
                .orElseThrow(() -> ApiException.notFound("Coupon not found"));
        coupon.setActive(false);
        couponRepository.save(coupon);
    }

    // --- apply / redeem -------------------------------------------------------------------

    /**
     * Validates a coupon against the cart subtotal and returns the discount amount.
     * When {@code lock} is true, takes a row lock (for place-order redemption).
     */
    @Transactional
    public AppliedCoupon apply(Long storeId, String rawCode, BigDecimal subtotal, boolean lock) {
        if (!StringUtils.hasText(rawCode)) {
            return AppliedCoupon.none();
        }
        String code = normalizeCode(rawCode);
        WholesaleCoupon coupon = (lock
                ? couponRepository.lockByStoreIdAndCode(storeId, code)
                : couponRepository.findByStoreIdAndCodeIgnoreCase(storeId, code))
                .orElseThrow(() -> ApiException.badRequest("Invalid coupon code"));
        assertUsable(coupon, subtotal);
        BigDecimal discount = calculateDiscount(coupon, subtotal);
        if (discount.signum() <= 0) {
            throw ApiException.badRequest("This coupon does not apply to the current order");
        }
        return new AppliedCoupon(coupon, coupon.getCode(), discount);
    }

    @Transactional
    public void redeem(WholesaleCoupon coupon, Long orderId, BigDecimal discountAmount) {
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.saveAndFlush(coupon);
        redemptionRepository.saveAndFlush(WholesaleCouponRedemption.builder()
                .orderId(orderId)
                .couponId(coupon.getId())
                .discountAmount(money(discountAmount))
                .build());
    }

    public BigDecimal calculateDiscount(WholesaleCoupon coupon, BigDecimal subtotal) {
        BigDecimal base = money(subtotal);
        BigDecimal discount;
        if ("FIXED".equals(coupon.getDiscountType())) {
            discount = money(coupon.getDiscountValue());
        } else {
            discount = base.multiply(money(coupon.getDiscountValue()))
                    .divide(BigDecimal.valueOf(100), 2, MONEY);
        }
        if (coupon.getMaxDiscount() != null) {
            discount = discount.min(money(coupon.getMaxDiscount()));
        }
        return discount.min(base).max(BigDecimal.ZERO.setScale(2, MONEY));
    }

    private void assertUsable(WholesaleCoupon coupon, BigDecimal subtotal) {
        if (!coupon.isActive()) {
            throw ApiException.badRequest("This coupon is no longer active");
        }
        Instant now = Instant.now();
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw ApiException.badRequest("This coupon is not active yet");
        }
        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            throw ApiException.badRequest("This coupon has expired");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw ApiException.badRequest("This coupon has reached its usage limit");
        }
        BigDecimal minOrder = coupon.getMinOrder() == null ? BigDecimal.ZERO : coupon.getMinOrder();
        if (money(subtotal).compareTo(money(minOrder)) < 0) {
            throw ApiException.badRequest("Order must be at least ₹" + money(minOrder) + " to use this coupon");
        }
    }

    private WholesaleStore requireOwnStore(Long userId) {
        accessService.requireSupplier(userId);
        return storeRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.forbidden("You do not have a wholesale store"));
    }

    private CouponResponse toResponse(WholesaleCoupon coupon) {
        return new CouponResponse(
                coupon.getUuid(),
                coupon.getCode(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinOrder(),
                coupon.getMaxDiscount(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                coupon.getUsageLimit(),
                coupon.getUsedCount(),
                coupon.isActive(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt());
    }

    private static String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw ApiException.badRequest("Coupon code is required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!"PERCENT".equals(value) && !"FIXED".equals(value)) {
            throw ApiException.badRequest("Discount type must be PERCENT or FIXED");
        }
        return value;
    }

    private static void validateDiscount(String type, BigDecimal value, BigDecimal maxDiscount) {
        if (value == null || value.signum() <= 0) {
            throw ApiException.badRequest("Discount value must be greater than 0");
        }
        if ("PERCENT".equals(type) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("Percent discount cannot exceed 100");
        }
        if (maxDiscount != null && maxDiscount.signum() < 0) {
            throw ApiException.badRequest("Max discount cannot be negative");
        }
    }

    private static void validateWindow(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw ApiException.badRequest("Coupon end date must be after the start date");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, MONEY);
        }
        return value.setScale(2, MONEY);
    }

    public record AppliedCoupon(WholesaleCoupon coupon, String code, BigDecimal discountAmount) {
        public static AppliedCoupon none() {
            return new AppliedCoupon(null, null, BigDecimal.ZERO.setScale(2, MONEY));
        }

        public boolean present() {
            return coupon != null;
        }
    }
}
