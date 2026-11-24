package com.catalogstudio.supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.catalogstudio")
@EntityScan(basePackages = "com.catalogstudio.wholesale.entity")
@EnableJpaRepositories(basePackages = "com.catalogstudio.wholesale.repository")
@ConfigurationPropertiesScan(basePackages = "com.catalogstudio.supplier")
@EnableScheduling
public class SupplierServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplierServiceApplication.class, args);
    }
}
