package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportKnowledgeArticle;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportKnowledgeArticleRepository extends JpaRepository<SupportKnowledgeArticle, Long> {

    Optional<SupportKnowledgeArticle> findByUuid(UUID uuid);
    Optional<SupportKnowledgeArticle> findBySlug(String slug);
    List<SupportKnowledgeArticle> findByActiveTrueAndVerifiedTrueAndLocaleOrderBySortOrderAsc(String locale);

    @Query("""
            select a from SupportKnowledgeArticle a
            where a.active = true and a.verified = true
              and a.locale = :locale
              and (
                    lower(a.title) like lower(concat('%', cast(:q as string), '%'))
                 or lower(coalesce(a.summary, '')) like lower(concat('%', cast(:q as string), '%'))
                 or lower(a.body) like lower(concat('%', cast(:q as string), '%'))
                 or lower(coalesce(a.tags, '')) like lower(concat('%', cast(:q as string), '%'))
              )
            order by a.sortOrder asc
            """)
    List<SupportKnowledgeArticle> searchVerified(
            @Param("locale") String locale,
            @Param("q") String q,
            Pageable pageable);

    Page<SupportKnowledgeArticle> findAllByOrderBySortOrderAsc(Pageable pageable);

}
