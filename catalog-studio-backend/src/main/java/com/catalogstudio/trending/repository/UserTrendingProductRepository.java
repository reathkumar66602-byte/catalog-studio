package com.catalogstudio.trending.repository;

import com.catalogstudio.trending.entity.UserTrendingProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTrendingProductRepository extends JpaRepository<UserTrendingProduct, Long> {

    List<UserTrendingProduct> findByUser_IdAndMarketplaceAndCategoryKeyOrderBySlotAsc(
            Long userId,
            String marketplace,
            String categoryKey
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserTrendingProduct p where p.user.id = :userId and p.marketplace = :marketplace and p.categoryKey = :categoryKey")
    void deleteByUser_IdAndMarketplaceAndCategoryKey(
            @Param("userId") Long userId,
            @Param("marketplace") String marketplace,
            @Param("categoryKey") String categoryKey
    );
}
