package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.coursplus.entity.ViewLog;

import java.time.Instant;
import java.util.List;

public interface ViewLogRepository extends JpaRepository<ViewLog, Long> {

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    /** Nombre d'adresses IP différentes utilisées par un compte sur une période. */
    @Query("select count(distinct v.ip) from ViewLog v where v.userId = :userId and v.createdAt > :since")
    long countDistinctIp(@Param("userId") Long userId, @Param("since") Instant since);

    /** [userId, nombre de lectures, nombre d'IP différentes] depuis une date, tous utilisateurs. */
    @Query("select v.userId, count(v), count(distinct v.ip) from ViewLog v where v.createdAt > :since group by v.userId")
    List<Object[]> activitySince(@Param("since") Instant since);
}
