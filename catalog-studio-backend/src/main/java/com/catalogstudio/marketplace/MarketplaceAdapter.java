package com.catalogstudio.marketplace;

import com.catalogstudio.product.entity.Product;
import java.util.List;
import java.util.Map;

public interface MarketplaceAdapter {

    String getMarketplace();

    List<String> getSupportedFields();

    Map<String, String> mapProduct(Product product);

    List<String> validate(Product product);

    Map<String, Object> prepareListing(Product product);
}
