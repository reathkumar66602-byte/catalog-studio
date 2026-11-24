package com.catalogstudio.campaign.job;

import com.catalogstudio.campaign.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignDispatchJob {

    private final CampaignService campaignService;

    @Value("${catalogstudio.campaign.batch-size:8}")
    private int batchSize;

    /**
     * Drains queued campaign recipients slowly.
     * Zoho Mail SMTP suits transactional mail; bulk at high rate hits 550 unusual-activity blocks.
     * Prefer ZeptoMail ({@code MAIL_PROVIDER=zeptomail}) for campaigns, with this low rate as safety.
     */
    @Scheduled(fixedDelayString = "${catalogstudio.campaign.dispatch-delay-ms:60000}")
    public void dispatch() {
        try {
            int size = Math.min(Math.max(batchSize, 1), 25);
            int processed = campaignService.processPendingBatch(size);
            if (processed > 0) {
                log.info("Campaign dispatch processed {} recipients (batchSize={})", processed, size);
            }
        } catch (RuntimeException ex) {
            log.warn("Campaign dispatch failed: {}", ex.toString());
        }
    }
}
