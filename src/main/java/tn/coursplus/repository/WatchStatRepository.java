package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coursplus.entity.WatchStat;

import java.util.List;
import java.util.Optional;

public interface WatchStatRepository extends JpaRepository<WatchStat, Long> {

    Optional<WatchStat> findByUserIdAndLessonId(Long userId, Long lessonId);

    List<WatchStat> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
