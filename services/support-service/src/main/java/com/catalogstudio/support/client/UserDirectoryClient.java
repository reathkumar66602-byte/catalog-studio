package com.catalogstudio.support.client;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface UserDirectoryClient {

    Optional<UserSnapshot> findById(Long userId);

    Map<Long, UserSnapshot> findByIds(Collection<Long> userIds);
}
