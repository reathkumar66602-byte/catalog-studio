package com.catalogstudio.internal;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class InternalApiSupport {

    public static final String HEADER = "X-Internal-Api-Key";

    private final CatalogStudioProperties properties;

    public void requireInternalKey(HttpServletRequest request) {
        String expected = properties.modules().internalApiKey();
        String provided = request.getHeader(HEADER);
        if (!StringUtils.hasText(expected) || !expected.equals(provided)) {
            throw ApiException.unauthorized("Invalid internal API key");
        }
    }
}
