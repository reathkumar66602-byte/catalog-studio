package com.catalogstudio.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.inventory.dto.InventoryAdjustRequest;
import com.catalogstudio.inventory.dto.VariantCreateRequest;
import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.entity.ProductVariant;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.inventory.repository.InventoryMovementRepository;
import com.catalogstudio.inventory.repository.ProductVariantRepository;
import com.catalogstudio.product.entity.Product;
import com.catalogstudio.product.repository.ProductRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock InventoryLevelRepository levelRepository;
    @Mock InventoryMovementRepository movementRepository;
    @Mock UserRepository userRepository;

    InventoryService service;
    Product product;

    @BeforeEach
    void setUp() {
        service = new InventoryService(
                productRepository, variantRepository, levelRepository, movementRepository, userRepository);
        User user = User.builder().id(1L).email("s@test.com").build();
        product = Product.builder().id(10L).uuid(UUID.randomUUID()).user(user).name("Tee").build();
    }

    @Test
    void createVariantSetsInitialStock() {
        when(productRepository.findByUuidAndUserId(product.getUuid(), 1L)).thenReturn(Optional.of(product));
        when(variantRepository.existsByProductIdAndSkuIgnoreCase(10L, "TEE-M")).thenReturn(false);
        when(variantRepository.save(any())).thenAnswer(inv -> {
            ProductVariant v = inv.getArgument(0);
            v.setId(99L);
            v.setUuid(UUID.randomUUID());
            return v;
        });
        when(levelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(product.getUser()));

        var response = service.createVariant(
                1L, product.getUuid(), new VariantCreateRequest("TEE-M", "M", "Red", null, null, 12));

        assertEquals(12, response.onHand());
        assertEquals(12, response.available());
        verify(movementRepository).save(any());
    }

    @Test
    void adjustRejectsOnHandBelowReserved() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(5L)
                .uuid(variantId)
                .product(product)
                .sku("TEE-M")
                .status(ProductVariant.VariantStatus.ACTIVE)
                .build();
        InventoryLevel level = InventoryLevel.builder()
                .variant(variant)
                .onHand(10)
                .reserved(4)
                .build();
        when(variantRepository.findByUuidAndUserId(variantId, 1L)).thenReturn(Optional.of(variant));
        when(levelRepository.findByVariantId(5L)).thenReturn(Optional.of(level));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.adjust(1L, new InventoryAdjustRequest(variantId, 2, "oops")));
        assertEquals(400, ex.getStatus().value());
    }
}
