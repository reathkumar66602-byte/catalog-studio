package com.catalogstudio.marketplace.repository;

import com.catalogstudio.marketplace.entity.MarketplaceChannelOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceChannelOrderRepository extends JpaRepository<MarketplaceChannelOrder, Long> {

    List<MarketplaceChannelOrder> findByUserIdAndMarketplaceOrderByOrderedAtDesc(Long userId, String marketplace);

    List<MarketplaceChannelOrder> findByUserIdOrderByOrderedAtDesc(Long userId);

    Optional<MarketplaceChannelOrder> findByUuidAndUserId(UUID uuid, Long userId);

    Optional<MarketplaceChannelOrder> findByConnectionIdAndExternalOrderIdAndExternalSubOrderId(
            Long connectionId, String externalOrderId, String externalSubOrderId);

    long countByUserIdAndMarketplaceAndStatusIgnoreCase(Long userId, String marketplace, String status);

    List<MarketplaceChannelOrder> findByUserIdAndMarketplaceAndLabelStatusIn(
            Long userId, String marketplace, List<String> labelStatuses);
}
