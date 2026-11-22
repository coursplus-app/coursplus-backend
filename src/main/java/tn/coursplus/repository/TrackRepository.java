package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coursplus.entity.Track;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {
    List<Track> findByUniversityIdOrderByNameAsc(Long universityId);
}
