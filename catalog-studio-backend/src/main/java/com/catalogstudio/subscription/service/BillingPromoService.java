package com.catalogstudio.subscription.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.subscription.dto.BillingPromoCodeRequest;
import com.catalogstudio.subscription.dto.BillingPromoCodeResponse;
import com.catalogstudio.subscription.entity.BillingPromoCode;
import com.catalogstudio.subscription.repository.BillingPromoCodeRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BillingPromoService {

    private final BillingPromoCodeRepository repository;

    @Transactional(readOnly = true)
    public List<BillingPromoCodeResponse> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<BillingPromoCodeResponse> listActiveForCheckout() {
        return repository.findByStatusOrderByCodeAsc("ACTIVE").stream()
                .filter(BillingPromoCode::isUsableToday)
                .map(this::toView)
                .toList();
    }

    @Transactional
    public BillingPromoCodeResponse create(BillingPromoCodeRequest request, Long actorId) {
        String code = normalizeCode(request.code());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw ApiException.badRequest("A promo with this code already exists");
        }
        BillingPromoCode promo = BillingPromoCode.builder()
                .code(code)
                .description(trim(request.description()))
                .discountType(normalizeType(request.discountType()))
                .discountValue(request.discountValue())
                .maxUses(request.maxUses())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .status(normalizeStatus(request.status()))
                .createdByUserId(actorId)
                .build();
        return toView(repository.save(promo));
    }

    @Transactional
    public BillingPromoCodeResponse update(UUID id, BillingPromoCodeRequest request) {
        BillingPromoCode promo = repository.findByUuid(id)
                .orElseThrow(() -> ApiException.notFound("Promo code not found"));
        String code = normalizeCode(request.code());
        if (!promo.getCode().equalsIgnoreCase(code) && repository.existsByCodeIgnoreCase(code)) {
            throw ApiException.badRequest("A promo with this code already exists");
        }
        promo.setCode(code);
        promo.setDescription(trim(request.description()));
        promo.setDiscountType(normalizeType(request.discountType()));
        promo.setDiscountValue(request.discountValue());
        promo.setMaxUses(request.maxUses());
        promo.setValidFrom(request.validFrom());
        promo.setValidUntil(request.validUntil());
        promo.setStatus(normalizeStatus(request.status()));
        return toView(promo);
    }

    @Transactional
    public void disable(UUID id) {
        BillingPromoCode promo = repository.findByUuid(id)
                .orElseThrow(() -> ApiException.notFound("Promo code not found"));
        promo.setStatus("DISABLED");
    }

    private BillingPromoCodeResponse toView(BillingPromoCode promo) {
        return new BillingPromoCodeResponse(
                promo.getUuid(),
                promo.getCode(),
                promo.getDescription(),
                promo.getDiscountType(),
                promo.getDiscountValue(),
                promo.getMaxUses(),
                promo.getUsedCount(),
                promo.getValidFrom(),
                promo.getValidUntil(),
                promo.getStatus(),
                promo.getCreatedAt()
        );
    }

    private static String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw ApiException.badRequest("Promo code is required");
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

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ACTIVE";
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(value) && !"DISABLED".equals(value)) {
            throw ApiException.badRequest("Status must be ACTIVE or DISABLED");
        }
        return value;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
