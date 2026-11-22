package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coursplus.entity.CourseDocument;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, Long> {
}
