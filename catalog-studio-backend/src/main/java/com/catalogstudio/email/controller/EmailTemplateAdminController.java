package com.catalogstudio.email.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.email.dto.EmailTemplateRequest;
import com.catalogstudio.email.dto.EmailTemplateResponse;
import com.catalogstudio.email.entity.EmailTemplate;
import com.catalogstudio.email.repository.EmailTemplateRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/email-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Email templates")
public class EmailTemplateAdminController {

    private final EmailTemplateRepository templateRepository;

    @GetMapping
    public ApiResponse<List<EmailTemplateResponse>> list() {
        return ApiResponse.ok(templateRepository.findAll().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<EmailTemplateResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(toResponse(template(id)));
    }

    @PostMapping
    @Transactional
    public ApiResponse<EmailTemplateResponse> create(@Valid @RequestBody EmailTemplateRequest request) {
        if (templateRepository.existsBySlugIgnoreCase(request.slug().trim())) {
            throw ApiException.conflict("A template with this slug already exists");
        }
        EmailTemplate saved = templateRepository.save(EmailTemplate.builder()
                .slug(request.slug().trim().toLowerCase())
                .name(request.name().trim())
                .subject(request.subject())
                .htmlBody(request.htmlBody())
                .textBody(request.textBody())
                .variablesJson(request.variables() == null ? List.of() : request.variables())
                .enabled(request.enabled() == null || request.enabled())
                .build());
        return ApiResponse.ok("Template saved", toResponse(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<EmailTemplateResponse> update(@PathVariable UUID id, @Valid @RequestBody EmailTemplateRequest request) {
        EmailTemplate template = template(id);
        template.setName(request.name().trim());
        template.setSubject(request.subject());
        template.setHtmlBody(request.htmlBody());
        template.setTextBody(request.textBody());
        if (request.variables() != null) {
            template.setVariablesJson(request.variables());
        }
        if (request.enabled() != null) {
            template.setEnabled(request.enabled());
        }
        return ApiResponse.ok("Template updated", toResponse(template));
    }

    private EmailTemplate template(UUID id) {
        return templateRepository.findByUuid(id).orElseThrow(() -> ApiException.notFound("Email template not found"));
    }

    private EmailTemplateResponse toResponse(EmailTemplate template) {
        return new EmailTemplateResponse(
                template.getUuid(),
                template.getSlug(),
                template.getName(),
                template.getSubject(),
                template.getHtmlBody(),
                template.getTextBody(),
                template.getVariablesJson(),
                template.isEnabled(),
                template.getUpdatedAt()
        );
    }
}
