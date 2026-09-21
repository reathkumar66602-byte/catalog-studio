package com.catalogstudio.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPublicAppOriginTest {

    @Test
    void prefersCatalogStudioOriginWhenPresent() {
        CatalogStudioProperties.Cors cors = new CatalogStudioProperties.Cors(List.of(
                "http://localhost:5173",
                "https://www.catalogstudio.in"));
        assertThat(cors.publicAppOrigin()).isEqualTo("https://www.catalogstudio.in");
    }

    @Test
    void usesLocalhostWhenThatIsAllThatIsConfigured() {
        CatalogStudioProperties.Cors cors = new CatalogStudioProperties.Cors(List.of("http://localhost:5173"));
        assertThat(cors.publicAppOrigin()).isEqualTo("http://localhost:5173");
    }
}
