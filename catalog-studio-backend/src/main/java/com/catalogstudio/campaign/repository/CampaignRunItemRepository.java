package com.catalogstudio.campaign.repository;

import com.catalogstudio.campaign.entity.CampaignRunItem;
import com.catalogstudio.campaign.entity.CampaignRunItem.ItemStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRunItemRepository extends JpaRepository<CampaignRunItem, Long> {

    @EntityGraph(attributePaths = {"campaignRun", "user"})
    List<CampaignRunItem> findByStatusOrderByIdAsc(ItemStatus status, Pageable pageable);

    long countByCampaignRunIdAndStatus(Long campaignRunId, ItemStatus status);
}
