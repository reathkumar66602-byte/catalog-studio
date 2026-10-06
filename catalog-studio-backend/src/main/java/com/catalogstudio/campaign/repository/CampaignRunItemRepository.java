package com.catalogstudio.campaign.repository;

import com.catalogstudio.campaign.entity.CampaignRunItem;
import com.catalogstudio.campaign.entity.CampaignRunItem.ItemStatus;
import com.catalogstudio.campaign.entity.NotificationTemplate.CampaignType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRunItemRepository extends JpaRepository<CampaignRunItem, Long> {

    @EntityGraph(attributePaths = {"campaignRun", "user"})
    List<CampaignRunItem> findByStatusOrderByIdAsc(ItemStatus status, Pageable pageable);

    long countByCampaignRunIdAndStatus(Long campaignRunId, ItemStatus status);

    @Query("""
            select i.user.id, count(i)
            from CampaignRunItem i
            where i.status = com.catalogstudio.campaign.entity.CampaignRunItem.ItemStatus.SENT
              and i.user.id in :userIds
              and i.campaignRun.campaignType = :campaignType
            group by i.user.id
            """)
    List<Object[]> countSentByUserIdsAndCampaignType(
            @Param("userIds") List<Long> userIds,
            @Param("campaignType") CampaignType campaignType);
}
