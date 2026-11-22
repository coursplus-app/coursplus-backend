package tn.coursplus.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.coursplus.dto.CatalogDtos.*;
import tn.coursplus.dto.TeacherDtos.*;
import tn.coursplus.dto.UserDto;
import tn.coursplus.entity.DocumentType;
import tn.coursplus.security.CurrentUser;
import tn.coursplus.service.TeacherService;

import java.util.List;
import java.util.Map;

/** Espace enseignant (rôle TEACHER obligatoire, voir SecurityConfig). */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    private Long me() {
        return CurrentUser.require().id();
    }

    @GetMapping("/courses")
    public List<CourseCardDto> courses() {
        return teacherService.myCourses(me());
    }

    @PostMapping("/courses")
    public CourseDetailDto create(@Valid @RequestBody CourseRequest req) {
        return teacherService.create(me(), req);
    }

    @PutMapping("/courses/{id}")
    public CourseDetailDto update(@PathVariable Long id, @Valid @RequestBody CourseRequest req) {
        return teacherService.update(me(), id, req);
    }

    /** { "published": true } */
    @PatchMapping("/courses/{id}/published")
    public CourseDetailDto publish(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return teacherService.setPublished(me(), id, Boolean.TRUE.equals(body.get("published")));
    }

    @DeleteMapping("/courses/{id}")
    public void delete(@PathVariable Long id) {
        teacherService.deleteCourse(me(), id);
    }

    /** Envoi d'une vidéo (formulaire multipart : title, freePreview, file). */
    @PostMapping(value = "/courses/{id}/lessons", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LessonDto addLesson(@PathVariable Long id,
                               @RequestParam(required = false) String title,
                               @RequestParam(defaultValue = "false") boolean freePreview,
                               @RequestParam("file") MultipartFile file) {
        return teacherService.addLesson(me(), id, title, freePreview, file);
    }

    @PatchMapping("/lessons/{lessonId}")
    public LessonDto updateLesson(@PathVariable Long lessonId, @RequestBody LessonUpdateRequest req) {
        return teacherService.updateLesson(me(), lessonId, req);
    }

    @DeleteMapping("/lessons/{lessonId}")
    public void deleteLesson(@PathVariable Long lessonId) {
        teacherService.deleteLesson(me(), lessonId);
    }

    /** Envoi d'un PDF (formulaire multipart : title, type = SUMMARY | EXERCISE, file). */
    @PostMapping(value = "/courses/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDto addDocument(@PathVariable Long id,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(defaultValue = "SUMMARY") DocumentType type,
                                   @RequestParam("file") MultipartFile file) {
        return teacherService.addDocument(me(), id, title, type, file);
    }

    @DeleteMapping("/documents/{documentId}")
    public void deleteDocument(@PathVariable Long documentId) {
        teacherService.deleteDocument(me(), documentId);
    }

    @PutMapping("/profile")
    public UserDto profile(@RequestBody ProfileRequest req) {
        return teacherService.updateProfile(me(), req);
    }

    @GetMapping("/stats")
    public StatsDto stats() {
        return teacherService.stats(me());
    }

    @GetMapping("/students")
    public List<StudentDto> students() {
        return teacherService.students(me());
    }
}
