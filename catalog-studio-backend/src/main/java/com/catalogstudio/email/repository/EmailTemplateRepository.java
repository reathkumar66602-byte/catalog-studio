package com.catalogstudio.email.repository;

import com.catalogstudio.email.entity.EmailTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {
    Optional<EmailTemplate> findBySlugIgnoreCase(String slug);
    Optional<EmailTemplate> findByUuid(UUID uuid);
    boolean existsBySlugIgnoreCase(String slug);
}
