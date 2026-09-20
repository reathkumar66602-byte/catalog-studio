package com.catalogstudio.extension.repository;

import com.catalogstudio.extension.entity.ExtensionUserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtensionUserSettingsRepository extends JpaRepository<ExtensionUserSettings, Long> {
}
