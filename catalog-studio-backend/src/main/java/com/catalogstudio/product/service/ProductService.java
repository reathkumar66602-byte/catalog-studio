package com.catalogstudio.product.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.product.dto.ProductResponse;
import com.catalogstudio.product.dto.ProductUpdateRequest;
import com.catalogstudio.product.entity.Product;
import com.catalogstudio.product.entity.ProductAttribute;
import com.catalogstudio.product.entity.ProductTitle;
import com.catalogstudio.product.repository.ProductAttributeRepository;
import com.catalogstudio.product.repository.ProductRepository;
import com.catalogstudio.product.repository.ProductTitleRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductAttributeRepository attributeRepository;
    private final ProductTitleRepository titleRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(Long userId, String query, String category, String color, String status, Pageable pageable) {
        Specification<Product> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (StringUtils.hasText(query)) {
                String like = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("productType")), like)
                ));
            }
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (StringUtils.hasText(color)) {
                predicates.add(cb.equal(root.get("primaryColor"), color));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), Product.ProductStatus.valueOf(status.toUpperCase())));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return productRepository.findAll(spec, pageable).map(ProductResponse::summary);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long userId, UUID id) {
        return ProductResponse.from(require(userId, id));
    }

    @Transactional
    public ProductResponse update(Long userId, UUID id, ProductUpdateRequest request) {
        Product product = require(userId, id);
        product.setName(request.name());
        product.setProductType(request.productType());
        product.setCategory(request.category());
        product.setSubcategory(request.subcategory());
        product.setGender(request.gender());
        product.setAgeGroup(request.ageGroup());
        product.setPrimaryColor(request.primaryColor());
        product.setPattern(request.pattern());
        product.setMaterial(request.material());
        product.setSleeveType(request.sleeveType());
        product.setNeckType(request.neckType());
        product.setCollarType(request.collarType());
        product.setFit(request.fit());
        product.setOccasion(request.occasion());
        product.setStyle(request.style());
        product.setDescription(request.description());
        if (StringUtils.hasText(request.status())) {
            product.setStatus(Product.ProductStatus.valueOf(request.status().toUpperCase()));
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("productType", request.productType());
        fields.put("category", request.category());
        fields.put("subCategory", request.subcategory());
        fields.put("gender", request.gender());
        fields.put("primaryColor", request.primaryColor());
        fields.put("pattern", request.pattern());
        fields.put("material", request.material());
        fields.put("sleeveType", request.sleeveType());
        fields.put("neckType", request.neckType());
        fields.put("collarType", request.collarType());
        fields.put("fit", request.fit());
        fields.put("occasion", request.occasion());
        fields.put("style", request.style());
        attributeRepository.deleteByProductId(product.getId());
        fields.forEach((name, value) -> {
            if (value != null && !value.isBlank()) {
                attributeRepository.save(ProductAttribute.builder()
                        .product(product)
                        .attributeName(name)
                        .attributeValue(value)
                        .source(ProductAttribute.AttributeSource.USER)
                        .build());
            }
        });
        if (request.titles() != null) {
            titleRepository.deleteByProductId(product.getId());
            for (int i = 0; i < request.titles().size(); i++) {
                String title = request.titles().get(i);
                titleRepository.save(ProductTitle.builder()
                        .product(product)
                        .title(title)
                        .selected(i == 0 || (request.selectedTitleId() == null && i == 0))
                        .source(ProductAttribute.AttributeSource.USER)
                        .build());
            }
        } else if (request.selectedTitleId() != null) {
            titleRepository.findByProductId(product.getId()).forEach(title ->
                    title.setSelected(title.getUuid().equals(request.selectedTitleId())));
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long userId, UUID id) {
        Product product = require(userId, id);
        productRepository.delete(product);
    }

    private Product require(Long userId, UUID id) {
        return productRepository.findByUuidAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }
}
