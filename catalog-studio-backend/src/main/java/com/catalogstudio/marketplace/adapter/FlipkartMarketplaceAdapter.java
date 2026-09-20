package com.catalogstudio.marketplace.adapter;

import com.catalogstudio.marketplace.repository.MarketplaceAttributeMappingRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FlipkartMarketplaceAdapter extends ConfigurableMarketplaceAdapter {

    public FlipkartMarketplaceAdapter(MarketplaceAttributeMappingRepository mappingRepository) {
        super(mappingRepository);
    }

    @Override
    public String getMarketplace() {
        return "FLIPKART";
    }

    @Override
    public List<String> getSupportedFields() {
        return List.of("title", "color", "pattern", "fabric", "style_code");
    }
}
