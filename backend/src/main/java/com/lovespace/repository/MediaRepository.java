package com.lovespace.repository;
import com.lovespace.domain.Media;
import java.util.*;
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
    List<Media> findByCoupleIdOrderById(Long coupleId);
    @Query("select coalesce(sum(m.byteSize), 0) from Media m where m.coupleId = :coupleId")
    long totalBytesByCoupleId(@Param("coupleId") Long coupleId);
}
