package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.coursplus.entity.Access;
import tn.coursplus.entity.OfferType;

import java.util.List;

public interface AccessRepository extends JpaRepository<Access, Long> {
    List<Access> findByStudentId(Long studentId);
    List<Access> findByStudentIdAndType(Long studentId, OfferType type);
    /** Tous les accès qui concernent un enseignant (achat d'un de ses cours ou abonnement à lui). */
    @Query("""
            select a from Access a
            left join a.course c
            left join c.teacher ct
            left join a.teacher t
            where ct.id = :teacherId or t.id = :teacherId
            """)
    List<Access> findAllForTeacher(@Param("teacherId") Long teacherId);
}
