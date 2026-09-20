package com.catalogstudio.marketplace.adapter;

import com.catalogstudio.marketplace.repository.MarketplaceAttributeMappingRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AmazonMarketplaceAdapter extends ConfigurableMarketplaceAdapter {

    public AmazonMarketplaceAdapter(MarketplaceAttributeMappingRepository mappingRepository) {
        super(mappingRepository);
    }

    @Override
    public String getMarketplace() {
        return "AMAZON";
    }

    @Override
    public List<String> getSupportedFields() {
        return List.of("item_name", "color_name", "pattern_type", "fabric_type", "bullet_point");
    }
}
