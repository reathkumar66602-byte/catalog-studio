package com.catalogstudio.tips.repository;

import com.catalogstudio.tips.entity.TipVideoSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipVideoSettingsRepository extends JpaRepository<TipVideoSettings, Long> {

    Optional<TipVideoSettings> findBySettingsKey(String settingsKey);
}
