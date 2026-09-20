package com.catalogstudio.product.dto;

import com.catalogstudio.product.entity.Product;
import com.catalogstudio.product.entity.ProductAttribute;
import com.catalogstudio.product.entity.ProductImage;
import com.catalogstudio.product.entity.ProductTitle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;

public record ProductResponse(
        UUID id,
        String name,
        String productType,
        String category,
        String subcategory,
        String gender,
        String ageGroup,
        String primaryColor,
        String pattern,
        String material,
        String sleeveType,
        String neckType,
        String collarType,
        String fit,
        String occasion,
        String style,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt,
        List<Image> images,
        List<Attribute> attributes,
        List<Title> titles
) {
    public record Image(UUID id, String url, int order, boolean primary) {}

    public record Attribute(String name, String value, BigDecimal confidence, String source) {}

    public record Title(UUID id, String title, boolean selected, String source) {}

    public static ProductResponse from(Product product) {
        List<Image> images = loadedImages(product).stream()
                .map(img -> new Image(img.getUuid(), img.getImageUrl(), img.getImageOrder(), img.isPrimary()))
                .toList();
        List<Attribute> attributes = loadedAttributes(product).stream()
                .map(a -> new Attribute(a.getAttributeName(), a.getAttributeValue(), a.getConfidence(), a.getSource().name()))
                .toList();
        List<Title> titles = loadedTitles(product).stream()
                .map(t -> new Title(t.getUuid(), t.getTitle(), t.isSelected(), t.getSource().name()))
                .toList();
        return new ProductResponse(
                product.getUuid(), product.getName(), product.getProductType(), product.getCategory(),
                product.getSubcategory(), product.getGender(), product.getAgeGroup(), product.getPrimaryColor(),
                product.getPattern(), product.getMaterial(), product.getSleeveType(), product.getNeckType(),
                product.getCollarType(), product.getFit(), product.getOccasion(), product.getStyle(),
                product.getDescription(), product.getStatus().name(), product.getCreatedAt(), product.getUpdatedAt(),
                images, attributes, titles
        );
    }

    public static ProductResponse summary(Product product) {
        List<ProductImage> productImages = loadedImages(product);
        String image = productImages.isEmpty()
                ? null
                : productImages.stream().filter(ProductImage::isPrimary).findFirst()
                .or(() -> productImages.stream().findFirst())
                .map(ProductImage::getImageUrl)
                .orElse(null);
        List<Image> images = image == null ? List.of() : List.of(new Image(null, image, 0, true));
        return new ProductResponse(
                product.getUuid(), product.getName(), product.getProductType(), product.getCategory(),
                product.getSubcategory(), product.getGender(), product.getAgeGroup(), product.getPrimaryColor(),
                product.getPattern(), product.getMaterial(), product.getSleeveType(), product.getNeckType(),
                product.getCollarType(), product.getFit(), product.getOccasion(), product.getStyle(),
                product.getDescription(), product.getStatus().name(), product.getCreatedAt(), product.getUpdatedAt(),
                images, List.of(), List.of()
        );
    }

    private static List<ProductImage> loadedImages(Product product) {
        return initialized(product.getImages());
    }

    private static List<ProductAttribute> loadedAttributes(Product product) {
        return initialized(product.getAttributes());
    }

    private static List<ProductTitle> loadedTitles(Product product) {
        return initialized(product.getTitles());
    }

    private static <T> List<T> initialized(List<T> values) {
        if (values == null) {
            return List.of();
        }
        try {
            Hibernate.initialize(values);
            return values;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
