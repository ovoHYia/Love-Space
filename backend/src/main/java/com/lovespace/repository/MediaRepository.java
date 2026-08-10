package com.lovespace.repository;
import com.lovespace.domain.Media;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByIdAndCoupleId(Long id, Long coupleId);
    @Query("""
            select m from Media m
            where m.id = :id and m.coupleId = :coupleId
              and (m.memoryId is null or exists (
                  select memory.id from Memory memory
                  where memory.id = m.memoryId and memory.deletedAt is null
              ))
            """)
    Optional<Media> findAccessibleByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") Long coupleId);
    List<Media> findByMemoryIdOrderById(Long memoryId);
    List<Media> findByMemoryIdIn(Collection<Long> memoryIds);
    List<Media> findByMemoryId(Long memoryId);
    List<Media> findByCoupleIdAndMediaTypeIgnoreCase(Long coupleId, String mediaType);
    List<Media> findByCoupleIdAndMediaTypeIn(Long coupleId, Collection<String> mediaTypes);
    List<Media> findByCoupleIdOrderById(Long coupleId);
    @Query(value = """
            select m.*
            from media m
            join memories mem on mem.id = m.memory_id
            where m.couple_id = :coupleId
              and m.media_type in (:mediaTypes)
              and mem.couple_id = :coupleId
              and mem.deleted_at is null
              and (:keywordPattern is null
                   or lower(mem.title) like :keywordPattern escape '\\'
                   or lower(mem.description) like :keywordPattern escape '\\'
                   or lower(mem.location) like :keywordPattern escape '\\')
              and (:tagKey is null or exists (
                  select 1 from memory_tags mt
                  where mt.memory_id = mem.id and lower(mt.tag) = :tagKey
              ))
            order by mem.event_at desc, m.id desc
            """,
            countQuery = """
            select count(*)
            from media m
            join memories mem on mem.id = m.memory_id
            where m.couple_id = :coupleId
              and m.media_type in (:mediaTypes)
              and mem.couple_id = :coupleId
              and mem.deleted_at is null
              and (:keywordPattern is null
                   or lower(mem.title) like :keywordPattern escape '\\'
                   or lower(mem.description) like :keywordPattern escape '\\'
                   or lower(mem.location) like :keywordPattern escape '\\')
              and (:tagKey is null or exists (
                  select 1 from memory_tags mt
                  where mt.memory_id = mem.id and lower(mt.tag) = :tagKey
              ))
            """, nativeQuery = true)
    Page<Media> findAlbumMedia(@Param("coupleId") Long coupleId,
                                @Param("mediaTypes") Collection<String> mediaTypes,
                                @Param("keywordPattern") String keywordPattern,
                                @Param("tagKey") String tagKey,
                                Pageable pageable);
    @Query(value = """
            select count(*)
            from media m
            join memories mem on mem.id = m.memory_id
            where m.couple_id = :coupleId
              and m.media_type in (:mediaTypes)
              and mem.couple_id = :coupleId
              and mem.deleted_at is null
              and (:keywordPattern is null
                   or lower(mem.title) like :keywordPattern escape '\\'
                   or lower(mem.description) like :keywordPattern escape '\\'
                   or lower(mem.location) like :keywordPattern escape '\\')
              and (:tagKey is null or exists (
                  select 1 from memory_tags mt
                  where mt.memory_id = mem.id and lower(mt.tag) = :tagKey
              ))
            """, nativeQuery = true)
    long countAlbumMedia(@Param("coupleId") Long coupleId,
                         @Param("mediaTypes") Collection<String> mediaTypes,
                         @Param("keywordPattern") String keywordPattern,
                         @Param("tagKey") String tagKey);
    @Query("select coalesce(sum(m.byteSize), 0) from Media m where m.coupleId = :coupleId")
    long totalBytesByCoupleId(@Param("coupleId") Long coupleId);
}
