package com.catalogstudio.product.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.product.dto.ProfileRequest;
import com.catalogstudio.product.dto.ProfileResponse;
import com.catalogstudio.product.dto.TemplateRequest;
import com.catalogstudio.product.dto.TemplateResponse;
import com.catalogstudio.product.entity.AutofillProfile;
import com.catalogstudio.product.entity.ListingTemplate;
import com.catalogstudio.product.repository.AutofillProfileRepository;
import com.catalogstudio.product.repository.ListingTemplateRepository;
import com.catalogstudio.user.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemplateProfileService {

    private final ListingTemplateRepository templateRepository;
    private final AutofillProfileRepository profileRepository;
    private final UserRepository userRepository;

    public Page<TemplateResponse> templates(Long userId, Pageable pageable) {
        return templateRepository.findByUserId(userId, pageable).map(this::toTemplate);
    }

    @Transactional
    public TemplateResponse createTemplate(Long userId, TemplateRequest request) {
        ListingTemplate saved = templateRepository.save(ListingTemplate.builder()
                .user(userRepository.getReferenceById(userId))
                .name(request.name())
                .marketplace(request.marketplace().toUpperCase())
                .productType(request.productType())
                .templateJson(request.attributes() == null ? Map.of() : request.attributes())
                .build());
        return toTemplate(saved);
    }

    @Transactional
    public TemplateResponse updateTemplate(Long userId, UUID id, TemplateRequest request) {
        ListingTemplate template = templateRepository.findByUuidAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Template not found"));
        template.setName(request.name());
        template.setMarketplace(request.marketplace().toUpperCase());
        template.setProductType(request.productType());
        template.setTemplateJson(request.attributes() == null ? Map.of() : request.attributes());
        return toTemplate(template);
    }

    @Transactional
    public TemplateResponse duplicateTemplate(Long userId, UUID id) {
        ListingTemplate source = templateRepository.findByUuidAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Template not found"));
        return createTemplate(userId, new TemplateRequest(
                source.getName() + " Copy", source.getMarketplace(), source.getProductType(), source.getTemplateJson()));
    }

    @Transactional
    public void deleteTemplate(Long userId, UUID id) {
        ListingTemplate template = templateRepository.findByUuidAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Template not found"));
        templateRepository.delete(template);
    }

    public Page<ProfileResponse> profiles(Long userId, Pageable pageable) {
        return profileRepository.findByUserId(userId, pageable).map(this::toProfile);
    }

    @Transactional
    public ProfileResponse createProfile(Long userId, ProfileRequest request) {
        AutofillProfile saved = profileRepository.save(AutofillProfile.builder()
                .user(userRepository.getReferenceById(userId))
                .name(request.name())
                .marketplace(request.marketplace().toUpperCase())
                .profileJson(request.profileJson() == null ? Map.of() : request.profileJson())
                .build());
        return toProfile(saved);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UUID id, ProfileRequest request) {
        AutofillProfile profile = profileRepository.findByUuidAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
        profile.setName(request.name());
        profile.setMarketplace(request.marketplace().toUpperCase());
        profile.setProfileJson(request.profileJson() == null ? Map.of() : request.profileJson());
        return toProfile(profile);
    }

    @Transactional
    public void deleteProfile(Long userId, UUID id) {
        AutofillProfile profile = profileRepository.findByUuidAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
        profileRepository.delete(profile);
    }

    private TemplateResponse toTemplate(ListingTemplate t) {
        return new TemplateResponse(t.getUuid(), t.getName(), t.getMarketplace(), t.getProductType(),
                t.getTemplateJson(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private ProfileResponse toProfile(AutofillProfile p) {
        return new ProfileResponse(p.getUuid(), p.getName(), p.getMarketplace(), p.getProfileJson(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
