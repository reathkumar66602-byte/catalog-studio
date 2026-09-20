package com.catalogstudio.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.email.service.EmailTemplateRenderer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {

    @Test
    void replacesDynamicPlaceholders() {
        String rendered = EmailTemplateRenderer.render(
                "Hi {{name}}, your {{appName}} code is {{otp}}.",
                Map.of("name", "Asha", "appName", "Catalog Studio", "otp", "482913"));
        assertThat(rendered).isEqualTo("Hi Asha, your Catalog Studio code is 482913.");
    }

    @Test
    void missingVariablesBecomeEmpty() {
        assertThat(EmailTemplateRenderer.render("Code {{otp}} for {{email}}", Map.of("otp", "111222")))
                .isEqualTo("Code 111222 for ");
    }
}
