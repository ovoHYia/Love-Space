package com.lovespace.repository;
import com.lovespace.domain.Couple;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface CoupleRepository extends JpaRepository<Couple, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select couple from Couple couple where couple.id = :id")
    Optional<Couple> findByIdForUpdate(@Param("id") Long id);
}
