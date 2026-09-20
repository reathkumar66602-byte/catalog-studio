package com.catalogstudio.site.repository;

import com.catalogstudio.site.entity.ClientStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientStoreRepository extends JpaRepository<ClientStore, Long> {
    Optional<ClientStore> findByUuid(UUID uuid);

    Optional<ClientStore> findBySlugIgnoreCase(String slug);

    Optional<ClientStore> findFirstByFeaturedTrueAndStatusIgnoreCase(String status);

    List<ClientStore> findAllByStatusIgnoreCaseOrderByFeaturedDescStoreNameAsc(String status);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);
}
