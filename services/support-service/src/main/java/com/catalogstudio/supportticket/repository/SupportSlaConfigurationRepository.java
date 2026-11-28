package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportSlaConfiguration;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportSlaConfigurationRepository extends JpaRepository<SupportSlaConfiguration, Long> {

    Optional<SupportSlaConfiguration> findFirstByActiveTrueOrderByIdAsc();
    Optional<SupportSlaConfiguration> findByUuid(UUID uuid);
    List<SupportSlaConfiguration> findAllByOrderByIdAsc();

}
