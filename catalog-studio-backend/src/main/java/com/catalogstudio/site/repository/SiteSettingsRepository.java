package com.catalogstudio.site.repository;

import com.catalogstudio.site.entity.SiteSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Long> {
    Optional<SiteSettings> findBySiteKey(String siteKey);
}
