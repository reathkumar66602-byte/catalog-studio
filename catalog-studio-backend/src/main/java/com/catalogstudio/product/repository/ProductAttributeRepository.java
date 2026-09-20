package com.catalogstudio.product.repository;

import com.catalogstudio.product.entity.ProductAttribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {
    List<ProductAttribute> findByProductId(Long productId);
    void deleteByProductId(Long productId);
}
