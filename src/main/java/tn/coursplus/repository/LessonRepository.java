package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coursplus.entity.Lesson;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
}
