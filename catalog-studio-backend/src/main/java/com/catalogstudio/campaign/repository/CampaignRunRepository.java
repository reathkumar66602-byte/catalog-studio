package com.catalogstudio.campaign.repository;

import com.catalogstudio.campaign.entity.CampaignRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRunRepository extends JpaRepository<CampaignRun, Long> {
    Optional<CampaignRun> findByUuid(UUID uuid);

    List<CampaignRun> findTop20ByOrderByCreatedAtDesc();
}
