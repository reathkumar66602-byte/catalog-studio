package com.catalogstudio.supplier.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserDirectoryClient {

    Optional<UserSnapshot> findById(Long userId);

    Map<Long, UserSnapshot> findByIds(Collection<Long> userIds);

    void updateMobile(Long userId, String mobile);
}
