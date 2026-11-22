package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coursplus.entity.Course;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByPublishedTrueOrderByCreatedAtDesc();

    List<Course> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);

    List<Course> findByTeacherIdAndPublishedTrue(Long teacherId);

    List<Course> findByTrackIdAndPublishedTrue(Long trackId);

    long countByPublishedTrue();
}
