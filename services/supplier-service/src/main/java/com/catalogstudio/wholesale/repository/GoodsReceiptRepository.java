package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.GoodsReceipt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    Optional<GoodsReceipt> findByUuid(UUID uuid);

    Optional<GoodsReceipt> findByOrderId(Long orderId);
}
