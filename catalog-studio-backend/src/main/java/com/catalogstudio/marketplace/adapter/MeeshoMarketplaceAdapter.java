package com.catalogstudio.marketplace.adapter;

import com.catalogstudio.marketplace.repository.MarketplaceAttributeMappingRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MeeshoMarketplaceAdapter extends ConfigurableMarketplaceAdapter {

    public MeeshoMarketplaceAdapter(MarketplaceAttributeMappingRepository mappingRepository) {
        super(mappingRepository);
    }

    @Override
    public String getMarketplace() {
        return "MEESHO";
    }

    @Override
    public List<String> getSupportedFields() {
        return List.of("productName", "color", "pattern", "fabric", "sleeve", "fit", "hsn", "gst", "packerDetails");
    }
}
