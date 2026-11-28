package com.catalogstudio.supportticket.knowledge;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.supportticket.SupportedLocales;
import com.catalogstudio.supportticket.dto.KnowledgeArticleRequest;
import com.catalogstudio.supportticket.dto.KnowledgeArticleResponse;
import com.catalogstudio.supportticket.entity.SupportKnowledgeArticle;
import com.catalogstudio.supportticket.repository.SupportKnowledgeArticleRepository;
import com.catalogstudio.supportticket.service.SupportMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeAdminService {

    private final SupportKnowledgeArticleRepository articleRepository;

    @Transactional(readOnly = true)
    public Page<KnowledgeArticleResponse> list(Pageable pageable) {
        return articleRepository.findAllByOrderBySortOrderAsc(pageable).map(SupportMapper::toKnowledge);
    }

    @Transactional(readOnly = true)
    public KnowledgeArticleResponse get(UUID uuid) {
        return SupportMapper.toKnowledge(articleRepository.findByUuid(uuid)
                .orElseThrow(() -> ApiException.notFound("Article not found")));
    }

    @Transactional
    public KnowledgeArticleResponse create(KnowledgeArticleRequest request) {
        articleRepository.findBySlug(request.slug().trim()).ifPresent(a -> {
            throw ApiException.conflict("Slug already exists");
        });
        Long userId = SecurityUtils.currentUserId();
        SupportKnowledgeArticle article = articleRepository.save(SupportKnowledgeArticle.builder()
                .slug(request.slug().trim())
                .title(request.title().trim())
                .summary(request.summary())
                .body(request.body())
                .locale(SupportedLocales.normalize(request.locale()))
                .category(request.category())
                .tags(request.tags())
                .active(request.active() == null || request.active())
                .verified(Boolean.TRUE.equals(request.verified()))
                .sortOrder(request.sortOrder() == null ? 100 : request.sortOrder())
                .createdBy(userId)
                .updatedBy(userId)
                .build());
        return SupportMapper.toKnowledge(article);
    }

    @Transactional
    public KnowledgeArticleResponse update(UUID uuid, KnowledgeArticleRequest request) {
        SupportKnowledgeArticle article = articleRepository.findByUuid(uuid)
                .orElseThrow(() -> ApiException.notFound("Article not found"));
        articleRepository.findBySlug(request.slug().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(article.getId())) {
                throw ApiException.conflict("Slug already exists");
            }
        });
        article.setSlug(request.slug().trim());
        article.setTitle(request.title().trim());
        article.setSummary(request.summary());
        article.setBody(request.body());
        article.setLocale(SupportedLocales.normalize(request.locale()));
        article.setCategory(request.category());
        article.setTags(request.tags());
        if (request.active() != null) {
            article.setActive(request.active());
        }
        if (request.verified() != null) {
            article.setVerified(request.verified());
        }
        if (request.sortOrder() != null) {
            article.setSortOrder(request.sortOrder());
        }
        article.setUpdatedBy(SecurityUtils.currentUserId());
        return SupportMapper.toKnowledge(articleRepository.save(article));
    }

    @Transactional
    public void delete(UUID uuid) {
        SupportKnowledgeArticle article = articleRepository.findByUuid(uuid)
                .orElseThrow(() -> ApiException.notFound("Article not found"));
        article.setActive(false);
        article.setUpdatedBy(SecurityUtils.currentUserId());
        articleRepository.save(article);
    }
}
