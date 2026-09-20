package com.catalogstudio.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.catalogstudio.marketplace.adapter.MeeshoMarketplaceAdapter;
import com.catalogstudio.marketplace.entity.MarketplaceAttributeMapping;
import com.catalogstudio.marketplace.repository.MarketplaceAttributeMappingRepository;
import com.catalogstudio.product.entity.Product;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeeshoMarketplaceAdapterTest {

    @Mock
    MarketplaceAttributeMappingRepository mappingRepository;

    @Test
    void mapsNavyBlueToMeeshoNavy() {
        when(mappingRepository.findByMarketplace("MEESHO")).thenReturn(List.of(
                MarketplaceAttributeMapping.builder()
                        .marketplace("MEESHO")
                        .internalAttribute("primaryColor")
                        .marketplaceAttribute("color")
                        .internalValue("Navy Blue")
                        .marketplaceValue("Navy")
                        .build()
        ));
        MeeshoMarketplaceAdapter adapter = new MeeshoMarketplaceAdapter(mappingRepository);
        Product product = Product.builder().primaryColor("Navy Blue").pattern("Checked").name("Shirt").build();
        var mapped = adapter.mapProduct(product);
        assertThat(mapped.get("color")).isEqualTo("Navy");
        assertThat(adapter.validate(product)).isEmpty();
        assertThat(adapter.prepareListing(product).get("autoSubmit")).isEqualTo(false);
    }
}
