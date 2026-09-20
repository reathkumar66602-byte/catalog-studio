package com.catalogstudio.product.repository;

import com.catalogstudio.product.entity.ProductTitle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTitleRepository extends JpaRepository<ProductTitle, Long> {
    List<ProductTitle> findByProductId(Long productId);
    void deleteByProductId(Long productId);
}
