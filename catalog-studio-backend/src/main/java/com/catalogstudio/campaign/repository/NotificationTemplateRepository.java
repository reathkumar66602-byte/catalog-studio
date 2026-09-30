package com.catalogstudio.campaign.repository;

import com.catalogstudio.campaign.entity.NotificationTemplate;
import com.catalogstudio.campaign.entity.NotificationTemplate.CampaignType;
import com.catalogstudio.campaign.entity.NotificationTemplate.Channel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByUuid(UUID uuid);

    Optional<NotificationTemplate> findBySlugIgnoreCase(String slug);

    Optional<NotificationTemplate> findFirstByCampaignTypeAndChannelAndEnabledTrue(
            CampaignType campaignType, Channel channel);

    List<NotificationTemplate> findAllByOrderByCampaignTypeAscChannelAsc();
}
