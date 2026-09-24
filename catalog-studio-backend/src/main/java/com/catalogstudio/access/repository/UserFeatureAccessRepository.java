package com.catalogstudio.access.repository;

import com.catalogstudio.access.entity.UserFeatureAccess;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeatureAccessRepository extends JpaRepository<UserFeatureAccess, Long> {

    List<UserFeatureAccess> findByUser_Id(Long userId);

    List<UserFeatureAccess> findByUser_IdIn(Collection<Long> userIds);

    Optional<UserFeatureAccess> findByUser_IdAndFeatureKey(Long userId, String featureKey);
}
