package com.catalogstudio.campaign.job;

import com.catalogstudio.campaign.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignDispatchJob {

    private final CampaignService campaignService;

    /** Drains queued campaign recipients in small batches. */
    @Scheduled(fixedDelayString = "${catalogstudio.campaign.dispatch-delay-ms:15000}")
    public void dispatch() {
        try {
            int processed = campaignService.processPendingBatch(40);
            if (processed > 0) {
                log.info("Campaign dispatch processed {} recipients", processed);
            }
        } catch (RuntimeException ex) {
            log.warn("Campaign dispatch failed: {}", ex.toString());
        }
    }
}
