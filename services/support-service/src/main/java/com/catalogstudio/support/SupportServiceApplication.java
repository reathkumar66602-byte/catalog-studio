package com.catalogstudio.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.catalogstudio")
@EntityScan(basePackages = "com.catalogstudio.supportticket.entity")
@EnableJpaRepositories(basePackages = "com.catalogstudio.supportticket.repository")
@ConfigurationPropertiesScan(basePackages = "com.catalogstudio.support")
@EnableScheduling
@EnableAsync
public class SupportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportServiceApplication.class, args);
    }
}
