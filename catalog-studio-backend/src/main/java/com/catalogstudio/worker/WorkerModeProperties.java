package com.catalogstudio.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * When {@code catalogstudio.worker.enabled=true} (or profile {@code worker}),
 * this process focuses on background jobs. The same jar still runs the API today
 * (modular monolith). A separate worker replica can be started later with
 * {@code SPRING_PROFILES_ACTIVE=prod,worker}.
 */
@ConfigurationProperties(prefix = "catalogstudio.worker")
public record WorkerModeProperties(boolean enabled) {

    public WorkerModeProperties {
        // defaults handled by binder / yaml
    }
}
