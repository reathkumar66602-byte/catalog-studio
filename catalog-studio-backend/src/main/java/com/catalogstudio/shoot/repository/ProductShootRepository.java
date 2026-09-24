package com.catalogstudio.shoot.repository;

import com.catalogstudio.shoot.entity.ProductShoot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductShootRepository extends JpaRepository<ProductShoot, Long> {

    @EntityGraph(attributePaths = "images")
    Optional<ProductShoot> findByUuidAndUser_Id(UUID uuid, Long userId);

    @EntityGraph(attributePaths = "images")
    List<ProductShoot> findTop12ByUser_IdOrderByCreatedAtDesc(Long userId);
}
