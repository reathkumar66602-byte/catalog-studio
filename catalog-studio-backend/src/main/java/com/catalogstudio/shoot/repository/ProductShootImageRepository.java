package com.catalogstudio.shoot.repository;

import com.catalogstudio.shoot.entity.ProductShootImage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductShootImageRepository extends JpaRepository<ProductShootImage, Long> {

    Optional<ProductShootImage> findByUuidAndShoot_User_Id(UUID uuid, Long userId);
}
