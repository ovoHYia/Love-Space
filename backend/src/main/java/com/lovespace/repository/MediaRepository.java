package com.lovespace.repository;
import com.lovespace.domain.Media;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByIdAndCoupleId(Long id, Long coupleId);
    List<Media> findByMemoryIdOrderById(Long memoryId);
    List<Media> findByMemoryIdIn(Collection<Long> memoryIds);
    List<Media> findByMemoryId(Long memoryId);
    List<Media> findByCoupleIdAndMediaTypeIgnoreCase(Long coupleId, String mediaType);
    @Query("select coalesce(sum(m.byteSize), 0) from Media m where m.coupleId = :coupleId")
    long totalBytesByCoupleId(@Param("coupleId") Long coupleId);
}
