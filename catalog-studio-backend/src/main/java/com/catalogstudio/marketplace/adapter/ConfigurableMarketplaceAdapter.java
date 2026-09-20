package com.catalogstudio.marketplace.adapter;

import com.catalogstudio.marketplace.MarketplaceAdapter;
import com.catalogstudio.marketplace.entity.MarketplaceAttributeMapping;
import com.catalogstudio.marketplace.repository.MarketplaceAttributeMappingRepository;
import com.catalogstudio.product.entity.Product;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class ConfigurableMarketplaceAdapter implements MarketplaceAdapter {

    private final MarketplaceAttributeMappingRepository mappingRepository;

    protected ConfigurableMarketplaceAdapter(MarketplaceAttributeMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @Override
    public Map<String, String> mapProduct(Product product) {
        Map<String, String> mapped = new LinkedHashMap<>();
        Map<String, String> internals = internals(product);
        List<MarketplaceAttributeMapping> mappings = mappingRepository.findByMarketplace(getMarketplace());
        internals.forEach((attr, value) -> {
            String marketplaceAttr = attr;
            String marketplaceValue = value;
            for (MarketplaceAttributeMapping mapping : mappings) {
                if (mapping.getInternalAttribute().equals(attr) && mapping.getInternalValue().equalsIgnoreCase(value)) {
                    marketplaceAttr = mapping.getMarketplaceAttribute();
                    marketplaceValue = mapping.getMarketplaceValue();
                    break;
                }
            }
            mapped.put(marketplaceAttr, marketplaceValue);
        });
        return mapped;
    }

    @Override
    public List<String> validate(Product product) {
        List<String> errors = new ArrayList<>();
        if (product.getName() == null || product.getName().isBlank()) {
            errors.add("Product title is required");
        }
        if (product.getPrimaryColor() == null || product.getPrimaryColor().isBlank()) {
            errors.add("Primary color is required");
        }
        return errors;
    }

    @Override
    public Map<String, Object> prepareListing(Product product) {
        Map<String, Object> listing = new LinkedHashMap<>();
        listing.put("marketplace", getMarketplace());
        listing.put("title", product.getName());
        listing.put("description", product.getDescription());
        listing.put("fields", mapProduct(product));
        listing.put("validationErrors", validate(product));
        listing.put("autoSubmit", false);
        return listing;
    }

    protected Map<String, String> internals(Product product) {
        Map<String, String> map = new LinkedHashMap<>();
        put(map, "primaryColor", product.getPrimaryColor());
        put(map, "pattern", product.getPattern());
        put(map, "material", product.getMaterial());
        put(map, "sleeveType", product.getSleeveType());
        put(map, "fit", product.getFit());
        put(map, "gender", product.getGender());
        put(map, "productType", product.getProductType());
        return map;
    }

    private void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    @SuppressWarnings("unused")
    protected Function<String, String> identity() {
        return Function.identity();
    }
}
