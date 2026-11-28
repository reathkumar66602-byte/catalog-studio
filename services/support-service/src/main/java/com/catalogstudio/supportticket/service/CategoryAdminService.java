package com.catalogstudio.supportticket.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supportticket.dto.CategoryRequest;
import com.catalogstudio.supportticket.dto.CategoryResponse;
import com.catalogstudio.supportticket.entity.SupportTicketCategory;
import com.catalogstudio.supportticket.repository.SupportTicketCategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryAdminService {

    private final SupportTicketCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(SupportMapper::toCategory)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(SupportMapper::toCategory)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        categoryRepository.findByCodeIgnoreCase(request.code().trim()).ifPresent(c -> {
            throw ApiException.conflict("Category code already exists");
        });
        SupportTicketCategory category = categoryRepository.save(SupportTicketCategory.builder()
                .code(request.code().trim().toUpperCase())
                .name(request.name().trim())
                .description(request.description())
                .active(request.active() == null || request.active())
                .sortOrder(request.sortOrder() == null ? 100 : request.sortOrder())
                .build());
        return SupportMapper.toCategory(category);
    }

    @Transactional
    public CategoryResponse update(UUID uuid, CategoryRequest request) {
        SupportTicketCategory category = categoryRepository.findByUuid(uuid)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        categoryRepository.findByCodeIgnoreCase(request.code().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(category.getId())) {
                throw ApiException.conflict("Category code already exists");
            }
        });
        category.setCode(request.code().trim().toUpperCase());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        if (request.active() != null) {
            category.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        return SupportMapper.toCategory(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID uuid) {
        SupportTicketCategory category = categoryRepository.findByUuid(uuid)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        category.setActive(false);
        categoryRepository.save(category);
    }
}
