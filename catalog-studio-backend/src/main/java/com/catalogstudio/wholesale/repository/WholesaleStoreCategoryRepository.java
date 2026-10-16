package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleStoreCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WholesaleStoreCategoryRepository
        extends JpaRepository<WholesaleStoreCategory, WholesaleStoreCategory.Key> {

    List<WholesaleStoreCategory> findByStoreId(Long storeId);

    List<WholesaleStoreCategory> findByStoreIdIn(Collection<Long> storeIds);

    boolean existsByStoreIdAndCategoryId(Long storeId, Long categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WholesaleStoreCategory sc WHERE sc.storeId = :storeId")
    int deleteByStoreId(@Param("storeId") Long storeId);
}