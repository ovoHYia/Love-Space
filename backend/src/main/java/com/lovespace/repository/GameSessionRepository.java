package com.lovespace.repository;

import com.lovespace.domain.GameSession;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    List<GameSession> findTop20ByCoupleIdOrderByUpdatedAtDesc(Long coupleId);
    List<GameSession> findByCoupleIdOrderById(Long coupleId);
    Optional<GameSession> findFirstByCoupleIdAndGameTypeAndStatusOrderByUpdatedAtDesc(
            Long coupleId, String gameType, String status);
    Optional<GameSession> findByIdAndCoupleId(Long id, Long coupleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select game from GameSession game where game.id = :id and game.coupleId = :coupleId")
    Optional<GameSession> findLockedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") Long coupleId);
}
