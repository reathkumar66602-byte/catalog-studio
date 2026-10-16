package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.wholesale.dto.GstLookupRequest;
import com.catalogstudio.wholesale.dto.GstLookupResponse;
import com.catalogstudio.wholesale.dto.StoreCreateRequest;
import com.catalogstudio.wholesale.dto.StoreResponse;
import com.catalogstudio.wholesale.dto.StoreUpdateRequest;
import com.catalogstudio.wholesale.entity.WholesaleCategory;
import com.catalogstudio.wholesale.entity.WholesaleStore;
import com.catalogstudio.wholesale.entity.WholesaleStoreCategory;
import com.catalogstudio.wholesale.repository.WholesaleCategoryRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreCategoryRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreRepository;
import com.catalogstudio.wholesale.service.GstVerificationService.GstLookupNames;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WholesaleStoreService {

    private final WholesaleAccessService accessService;
    private final GstVerificationService gstVerificationService;
    private final WholesaleStoreRepository storeRepository;
    private final WholesaleCategoryRepository categoryRepository;
    private final WholesaleStoreCategoryRepository storeCategoryRepository;
    private final WholesaleMapper mapper;

    @Transactional(readOnly = true)
    public Optional<StoreResponse> getMyStore(Long userId) {
        accessService.requireSupplier(userId);
        return storeRepository.findByOwnerUserId(userId).map(store -> mapper.store(store, true));
    }

    /**
     * Verifies GSTIN via gstinapi and returns the locked store name (trade name, else legal name).
     * Does not create the store.
     */
    @Transactional(readOnly = true)
    public GstLookupResponse lookupGst(Long userId, GstLookupRequest request) {
        accessService.requireSupplier(userId);
        String gstin = GstVerificationService.normalizeGstin(request.gstin());
        if (storeRepository.existsByGstin(gstin)) {
            throw ApiException.conflict("A store is already registered with this GSTIN");
        }
        GstLookupNames gst = gstVerificationService.lookup(gstin);
        return new GstLookupResponse(
                gst.gstin(),
                gst.legalName(),
                gst.tradeName(),
                gst.storeName(),
                gst.status());
    }

    @Transactional
    public StoreResponse create(Long userId, StoreCreateRequest request) {
        accessService.requireSupplier(userId);
        if (storeRepository.findByOwnerUserId(userId).isPresent()) {
            throw ApiException.conflict("You already have a wholesale store");
        }
        String gstin = GstVerificationService.normalizeGstin(request.gstin());
        if (storeRepository.existsByGstin(gstin)) {
            throw ApiException.conflict("A store is already registered with this GSTIN");
        }
        List<WholesaleCategory> categories = resolveCategories(request.categoryIds());
        if (categories.isEmpty()) {
            throw ApiException.badRequest("Select at least one category for your store");
        }

        // Store name is always taken from GST (trade name preferred) — never trust a free-typed name.
        GstLookupNames gst = gstVerificationService.lookup(gstin);
        String storeName = gst.storeName();

        WholesaleStore store = WholesaleStore.builder()
                .ownerUserId(userId)
                .storeName(storeName)
                .slug(uniqueSlug(storeName))
                .gstin(gstin)
                .gstLegalName(StringUtils.hasText(gst.legalName()) ? gst.legalName() : storeName)
                .gstVerifiedAt(Instant.now())
                .gstVerificationStatus("VERIFIED")
                .description(trimToNull(request.description()))
                .city(trimToNull(request.city()))
                .state(trimToNull(request.state()))
                .phone(trimToNull(request.phone()))
                .email(trimToNull(request.email()))
                .status(WholesaleStore.Status.ACTIVE)
                .build();
        try {
            store = storeRepository.saveAndFlush(store);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Store could not be created (duplicate store, name or GSTIN)");
        }
        syncCategories(store.getId(), categories);
        return mapper.store(store, true);
    }

    @Transactional
    public StoreResponse update(Long userId, StoreUpdateRequest request) {
        accessService.requireSupplier(userId);
        WholesaleStore store = storeRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Create your store first"));
        if (store.getStatus() == WholesaleStore.Status.SUSPENDED) {
            throw ApiException.forbidden("This store is suspended. Contact support.");
        }
        if (request.description() != null) {
            store.setDescription(trimToNull(request.description()));
        }
        if (request.city() != null) {
            store.setCity(trimToNull(request.city()));
        }
        if (request.state() != null) {
            store.setState(trimToNull(request.state()));
        }
        if (StringUtils.hasText(request.phone())) {
            store.setPhone(request.phone().trim());
        }
        if (StringUtils.hasText(request.email())) {
            store.setEmail(request.email().trim());
        }
        store = storeRepository.save(store);
        if (request.categoryIds() != null) {
            List<WholesaleCategory> categories = resolveCategories(request.categoryIds());
            if (categories.isEmpty()) {
                throw ApiException.badRequest("Select at least one category for your store");
            }
            syncCategories(store.getId(), categories);
        }
        return mapper.store(store, true);
    }

    /** Store for a supplier user, or 404 when none exists yet. */
    @Transactional(readOnly = true)
    public WholesaleStore requireStore(Long userId) {
        return storeRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Create your store first"));
    }

    private List<WholesaleCategory> resolveCategories(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> unique = new LinkedHashSet<>(ids);
        List<WholesaleCategory> found = categoryRepository.findByUuidIn(unique).stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .toList();
        if (found.size() != unique.size()) {
            throw ApiException.badRequest("One or more selected categories are invalid");
        }
        return found;
    }

    private void syncCategories(Long storeId, List<WholesaleCategory> categories) {
        storeCategoryRepository.deleteByStoreId(storeId);
        storeCategoryRepository.saveAll(categories.stream()
                .map(c -> WholesaleStoreCategory.builder().storeId(storeId).categoryId(c.getId()).build())
                .toList());
    }

    private String uniqueSlug(String name) {
        String base = WholesaleSlugs.slugify(name, 150);
        if (base.isEmpty()) {
            base = "store";
        }
        String slug = base;
        int attempt = 0;
        while (storeRepository.existsBySlug(slug)) {
            attempt++;
            slug = base + "-" + (attempt < 3 ? String.valueOf(attempt + 1) : WholesaleSlugs.randomSuffix(5));
        }
        return slug;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
