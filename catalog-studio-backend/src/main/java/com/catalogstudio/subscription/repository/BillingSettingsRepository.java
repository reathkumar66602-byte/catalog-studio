package com.catalogstudio.subscription.repository;

import com.catalogstudio.subscription.entity.BillingSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingSettingsRepository extends JpaRepository<BillingSettings, Long> {
    Optional<BillingSettings> findBySettingsKey(String settingsKey);
}
