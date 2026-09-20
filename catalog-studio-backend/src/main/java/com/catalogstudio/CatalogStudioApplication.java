package com.catalogstudio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@ConfigurationPropertiesScan
public class CatalogStudioApplication {

    public static void main(String[] args) {
        loadDotEnv(List.of(Path.of(".env"), Path.of("..").resolve(".env")));
        SpringApplication.run(CatalogStudioApplication.class, args);
    }

    static void loadDotEnv(List<Path> candidates) {
        for (Path path : candidates) {
            Path resolved = path.toAbsolutePath().normalize();
            if (!Files.isRegularFile(resolved)) {
                continue;
            }
            try {
                for (String raw : Files.readAllLines(resolved, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = line.substring(0, eq).trim();
                    String value = unquote(line.substring(eq + 1).trim());
                    if (key.isEmpty() || System.getenv(key) != null) {
                        continue;
                    }
                    if (System.getProperty(key) == null) {
                        System.setProperty(key, value);
                    }
                }
                return;
            } catch (IOException ignored) {
                // fall through to the next candidate
            }
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
