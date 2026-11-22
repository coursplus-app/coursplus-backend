package tn.coursplus.controller;

import org.springframework.web.bind.annotation.*;
import tn.coursplus.dto.CatalogDtos.*;
import tn.coursplus.service.CatalogService;

import java.util.List;

/** Catalogue public (pas besoin d'être connecté). */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/catalog/universities")
    public List<UniversityDto> universities() {
        return catalogService.universities();
    }

    @GetMapping("/catalog/teachers")
    public List<TeacherDto> teachers() {
        return catalogService.teachers();
    }

    /** Exemple : /api/courses?universityId=1&trackId=2&teacherId=3&q=spring */
    @GetMapping("/courses")
    public List<CourseCardDto> courses(@RequestParam(required = false) Long universityId,
                                       @RequestParam(required = false) Long trackId,
                                       @RequestParam(required = false) Long teacherId,
                                       @RequestParam(required = false) String q) {
        return catalogService.search(universityId, trackId, teacherId, q);
    }

    @GetMapping("/courses/{id}")
    public CourseDetailDto course(@PathVariable Long id) {
        return catalogService.detail(id);
    }
}
