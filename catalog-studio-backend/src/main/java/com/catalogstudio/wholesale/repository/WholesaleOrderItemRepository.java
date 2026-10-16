package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleOrderItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WholesaleOrderItemRepository extends JpaRepository<WholesaleOrderItem, Long> {

    List<WholesaleOrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    List<WholesaleOrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);
}