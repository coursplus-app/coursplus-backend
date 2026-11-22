package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.coursplus.entity.WatchHour;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WatchHourRepository extends JpaRepository<WatchHour, Long> {

    Optional<WatchHour> findByUserIdAndHourStart(Long userId, Instant hourStart);

    @Query("select coalesce(sum(h.seconds), 0) from WatchHour h where h.userId = :userId and h.hourStart > :since")
    long totalSince(@Param("userId") Long userId, @Param("since") Instant since);

    /** [userId, secondes regardées] depuis une date, tous utilisateurs. */
    @Query("select h.userId, sum(h.seconds) from WatchHour h where h.hourStart > :since group by h.userId")
    List<Object[]> totalsSince(@Param("since") Instant since);

    /** Utilisateurs ayant dépassé la limite de secondes sur une heure d'horloge (lectures simultanées). */
    @Query("select distinct h.userId from WatchHour h where h.hourStart > :since and h.seconds > :limit")
    List<Long> usersOverHourLimit(@Param("since") Instant since, @Param("limit") int limit);
}
