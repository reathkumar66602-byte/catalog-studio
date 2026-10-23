package com.catalogstudio.tips.repository;

import com.catalogstudio.tips.entity.TipVideo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TipVideoRepository extends JpaRepository<TipVideo, Long> {

    Optional<TipVideo> findByUuid(UUID uuid);

    List<TipVideo> findAllByOrderByCreatedAtDesc();

    List<TipVideo> findByKindOrderByCreatedAtDesc(TipVideo.Kind kind);

    Optional<TipVideo> findBySeriesKeyAndLanguage(UUID seriesKey, TipVideo.Language language);

    Optional<TipVideo> findFirstByOrderByCreatedAtAsc();

    long count();

    @Query("select coalesce(sum(t.sizeBytes), 0) from TipVideo t")
    long sumSizeBytes();
}
