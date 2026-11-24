package com.catalogstudio.tips.repository;

import com.catalogstudio.tips.entity.TipVideoComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TipVideoCommentRepository extends JpaRepository<TipVideoComment, Long> {

    Optional<TipVideoComment> findByUuid(UUID uuid);

    List<TipVideoComment> findByTipVideo_IdOrderByCreatedAtAsc(Long tipVideoId);

    @Query("""
            select c from TipVideoComment c
            join fetch c.user u
            join fetch c.tipVideo v
            where c.parent is null
            order by c.createdAt desc
            """)
    List<TipVideoComment> findInboxRoots();
}
