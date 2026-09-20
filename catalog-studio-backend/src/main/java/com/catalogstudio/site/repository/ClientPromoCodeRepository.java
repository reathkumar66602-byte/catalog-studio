package com.catalogstudio.site.repository;

import com.catalogstudio.site.entity.ClientPromoCode;
import com.catalogstudio.site.entity.ClientStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientPromoCodeRepository extends JpaRepository<ClientPromoCode, Long> {
    Optional<ClientPromoCode> findByUuid(UUID uuid);

    Optional<ClientPromoCode> findByCodeIgnoreCase(String code);

    List<ClientPromoCode> findByClientAndStatusIgnoreCaseOrderByCreatedAtDesc(ClientStore client, String status);

    List<ClientPromoCode> findAllByOrderByCreatedAtDesc();

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
