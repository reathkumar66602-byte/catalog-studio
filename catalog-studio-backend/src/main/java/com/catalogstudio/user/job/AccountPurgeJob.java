package com.catalogstudio.user.job;

import com.catalogstudio.user.service.AccountLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountPurgeJob {

    private final AccountLifecycleService accountLifecycleService;

    /** Daily: permanently delete accounts deactivated 15+ days ago. */
    @Scheduled(cron = "${catalogstudio.account.purge-cron:0 30 3 * * *}")
    public void purge() {
        try {
            int purged = accountLifecycleService.purgeDueAccounts();
            if (purged > 0) {
                log.info("Purged {} deactivated accounts", purged);
            }
        } catch (RuntimeException ex) {
            log.warn("Account purge job failed: {}", ex.toString());
        }
    }
}
