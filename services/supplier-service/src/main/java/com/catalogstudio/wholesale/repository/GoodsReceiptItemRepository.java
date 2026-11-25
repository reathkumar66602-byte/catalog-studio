package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.GoodsReceiptItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, Long> {

    List<GoodsReceiptItem> findByReceiptIdOrderByIdAsc(Long receiptId);
}
