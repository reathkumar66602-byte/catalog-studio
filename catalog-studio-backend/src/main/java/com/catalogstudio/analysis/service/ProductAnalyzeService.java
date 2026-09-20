package com.catalogstudio.analysis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.catalogstudio.ai.AIProductAnalysisService;
import com.catalogstudio.ai.AIProductAnalysisService.ImagePayload;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisRequest;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import com.catalogstudio.analysis.dto.AnalysisResultResponse;
import com.catalogstudio.analysis.entity.ProductAnalysis;
import com.catalogstudio.analysis.repository.ProductAnalysisRepository;
import com.catalogstudio.audit.service.AuditService;
import com.catalogstudio.business.repository.BusinessRepository;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.product.entity.Product;
import com.catalogstudio.product.entity.ProductAttribute;
import com.catalogstudio.product.entity.ProductImage;
import com.catalogstudio.product.entity.ProductTitle;
import com.catalogstudio.product.repository.ProductAttributeRepository;
import com.catalogstudio.product.repository.ProductRepository;
import com.catalogstudio.product.repository.ProductTitleRepository;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductAnalyzeService {

    private final CatalogStudioProperties properties;
    private final StorageService storageService;
    private final AIProductAnalysisService aiProductAnalysisService;
    private final ProductRepository productRepository;
    private final ProductAttributeRepository attributeRepository;
    private final ProductTitleRepository titleRepository;
    private final ProductAnalysisRepository analysisRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AnalysisResultResponse analyze(
            Long userId,
            List<MultipartFile> images,
            Integer primaryIndex,
            String marketplace,
            String categoryHint,
            String productTypeHint
    ) {
        validateImages(images);
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        Product product = Product.builder()
                .user(user)
                .business(businessRepository.findByUserId(userId).orElse(null))
                .status(Product.ProductStatus.DRAFT)
                .build();
        productRepository.save(product);

        List<StorageService.StoredFile> stored = new ArrayList<>();
        List<ImagePayload> payloads = new ArrayList<>();
        int primary = primaryIndex == null ? 0 : primaryIndex;
        for (int i = 0; i < images.size(); i++) {
            MultipartFile file = images.get(i);
            StorageService.StoredFile storedFile = storageService.store(file);
            stored.add(storedFile);
            ProductImage image = ProductImage.builder()
                    .product(product)
                    .imageUrl(storedFile.publicUrl())
                    .storageKey(storedFile.storageKey())
                    .imageOrder(i)
                    .primary(i == primary)
                    .build();
            product.getImages().add(image);
            payloads.add(new ImagePayload(storedFile.bytes(), storedFile.contentType(), file.getOriginalFilename()));
        }

        ProductAnalysis analysis = analysisRepository.save(ProductAnalysis.builder()
                .product(product)
                .user(user)
                .provider(properties.ai().provider())
                .model(properties.ai().model())
                .status(ProductAnalysis.AnalysisStatus.PENDING)
                .requestData(Map.of(
                        "imageCount", images.size(),
                        "marketplace", marketplace == null ? "" : marketplace,
                        "categoryHint", categoryHint == null ? "" : categoryHint,
                        "productTypeHint", productTypeHint == null ? "" : productTypeHint
                ))
                .build());

        try {
            ProductAnalysisResponse ai = aiProductAnalysisService.analyze(
                    new ProductAnalysisRequest(payloads, marketplace, categoryHint, productTypeHint));
            applyAnalysis(product, ai);
            analysis.setStatus(ProductAnalysis.AnalysisStatus.COMPLETED);
            analysis.setProvider(ai.provider());
            analysis.setModel(ai.model());
            analysis.setOverallConfidence(decimal(ai.overallConfidence()));
            analysis.setResponseData(objectMapper.convertValue(ai, new TypeReference<Map<String, Object>>() {}));
            analysisRepository.save(analysis);
            auditService.log(user, "PRODUCT_ANALYZED", "PRODUCT", product.getUuid().toString(), null, Map.of("analysisId", analysis.getUuid().toString()));
            return toResponse(analysis, product, ai);
        } catch (RuntimeException ex) {
            analysis.setStatus(ProductAnalysis.AnalysisStatus.FAILED);
            analysis.setErrorMessage(ex.getMessage());
            analysisRepository.save(analysis);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public AnalysisResultResponse get(Long userId, UUID analysisId) {
        ProductAnalysis analysis = analysisRepository.findByUuidAndUserId(analysisId, userId)
                .orElseThrow(() -> ApiException.notFound("Analysis not found"));
        ProductAnalysisResponse ai = analysis.getResponseData() == null
                ? null
                : objectMapper.convertValue(analysis.getResponseData(), ProductAnalysisResponse.class);
        return toResponse(analysis, analysis.getProduct(), ai);
    }

    private void applyAnalysis(Product product, ProductAnalysisResponse ai) {
        product.setProductType(ai.productType());
        product.setCategory(ai.category());
        product.setSubcategory(ai.subCategory());
        product.setGender(ai.gender());
        product.setAgeGroup(ai.ageGroup());
        product.setPrimaryColor(ai.primaryColor());
        product.setPattern(ai.pattern());
        product.setMaterial(ai.material());
        product.setSleeveType(ai.sleeveType());
        product.setNeckType(ai.neckType());
        product.setCollarType(ai.collarType());
        product.setFit(ai.fit());
        product.setOccasion(ai.occasion());
        product.setStyle(ai.style());
        product.setDescription(ai.productDescription());
        if (ai.suggestedTitles() != null && !ai.suggestedTitles().isEmpty()) {
            product.setName(ai.suggestedTitles().get(0));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("productType", ai.productType());
        fields.put("category", ai.category());
        fields.put("subCategory", ai.subCategory());
        fields.put("gender", ai.gender());
        fields.put("primaryColor", ai.primaryColor());
        fields.put("pattern", ai.pattern());
        fields.put("material", ai.material());
        fields.put("sleeveType", ai.sleeveType());
        fields.put("neckType", ai.neckType());
        fields.put("collarType", ai.collarType());
        fields.put("fit", ai.fit());
        fields.put("occasion", ai.occasion());
        fields.put("style", ai.style());
        fields.forEach((name, value) -> {
            if (value != null) {
                attributeRepository.save(ProductAttribute.builder()
                        .product(product)
                        .attributeName(name)
                        .attributeValue(String.valueOf(value))
                        .source(ProductAttribute.AttributeSource.AI)
                        .build());
            }
        });
        if (ai.suggestedTitles() != null) {
            for (int i = 0; i < ai.suggestedTitles().size(); i++) {
                titleRepository.save(ProductTitle.builder()
                        .product(product)
                        .title(ai.suggestedTitles().get(i))
                        .selected(i == 0)
                        .source(ProductAttribute.AttributeSource.AI)
                        .build());
            }
        }
    }

    private AnalysisResultResponse toResponse(ProductAnalysis analysis, Product product, ProductAnalysisResponse ai) {
        List<AnalysisResultResponse.ImagePayload> images = product == null ? List.of() : product.getImages().stream()
                .map(img -> new AnalysisResultResponse.ImagePayload(img.getUuid(), img.getImageUrl(), img.getImageOrder(), img.isPrimary()))
                .toList();
        AnalysisResultResponse.ProductPayload payload = ai == null
                ? null
                : new AnalysisResultResponse.ProductPayload(
                        ai.productType(), ai.category(), ai.subCategory(), ai.gender(), ai.ageGroup(),
                        ai.primaryColor(), ai.secondaryColors(), ai.colorConfidence(), ai.pattern(), ai.patternConfidence(),
                        ai.material(), ai.materialConfidence(), ai.sleeveType(), ai.neckType(), ai.collarType(),
                        ai.fit(), ai.occasion(), ai.style(), ai.productDescription(), ai.suggestedTitles(),
                        ai.suggestedDescriptions(), ai.keywords(), ai.overallConfidence(), ai.uncertainFields(), images
                );
        return new AnalysisResultResponse(
                analysis.getUuid(),
                product == null ? null : product.getUuid(),
                analysis.getStatus().name(),
                analysis.getProvider(),
                analysis.getModel(),
                analysis.getOverallConfidence(),
                analysis.getCreatedAt(),
                payload
        );
    }

    private void validateImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw ApiException.badRequest("At least one image is required");
        }
        if (images.size() > properties.upload().maxImages()) {
            throw ApiException.badRequest("A maximum of 5 images can be uploaded");
        }
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
