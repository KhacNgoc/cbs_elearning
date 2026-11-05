package com.bidv.coretraining.course.service.impl;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.document.service.AssetAccessService;
import com.bidv.coretraining.document.service.DocumentStorageService;
import com.bidv.coretraining.enums.DocumentPurpose;
import com.bidv.coretraining.enums.DocumentType;
import com.bidv.coretraining.enums.VideoSource;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.mapper.CourseMapper;
import com.bidv.coretraining.policy.LessonContentPolicy;
import com.bidv.coretraining.training.dto.TrainingStructureDto;
import com.bidv.coretraining.training.service.TrainingStructureService;
import com.bidv.coretraining.video.service.VideoStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.course.entity.Chapter;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.course.service.CourseContentAdminService;
import com.bidv.coretraining.document.dto.LessonDocumentDto;
import com.bidv.coretraining.document.dto.UploadResultDto;
import com.bidv.coretraining.document.entity.LessonDocument;
import com.bidv.coretraining.lesson.dto.LessonDto;
import com.bidv.coretraining.lesson.dto.ProcedureSectionDto;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.entity.ProcedureSection;
import com.bidv.coretraining.lesson.repository.LessonRepository;

@Service
public class CourseContentAdminServiceImpl implements CourseContentAdminService {
    private final AuthorizationService authorization;
    private final AssetAccessService assets;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final CourseMapper courseMapper;
    private final TrainingStructureService structure;
    private final DocumentStorageService documentStorage;
    private final VideoStorageService videoStorage;

    public CourseContentAdminServiceImpl(AuthorizationService authorization, AssetAccessService assets,
            CourseRepository courseRepository, LessonRepository lessonRepository, CourseMapper courseMapper,
            TrainingStructureService structure, DocumentStorageService documentStorage,
            VideoStorageService videoStorage) {
        this.authorization = authorization;
        this.assets = assets;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.courseMapper = courseMapper;
        this.structure = structure;
        this.documentStorage = documentStorage;
        this.videoStorage = videoStorage;
    }

    // Course CRUD
    @Override
    @Transactional
    public CourseDto createCourse(CourseDto courseDto) {
        return structure.saveCourse(null, courseRequest(courseDto));
    }

    @Override
    @Transactional
    public CourseDto updateCourse(String id, CourseDto courseDto) {
        return structure.saveCourse(id, courseRequest(courseDto));
    }

    private TrainingStructureDto.CourseRequest courseRequest(CourseDto dto) {
        return new TrainingStructureDto.CourseRequest(dto.getCode(), dto.getName(),
                dto.getProgramId(), dto.getDescription(), dto.getIconName(), dto.getVersion(), dto.getThumbnail(),
                dto.getBusinessModule(), dto.getLevel(), dto.getKeywords(), dto.getCompletionDeadline(), dto.getMandatoryUnitIds(), dto.getOptionalUnitIds(), dto.getUnitIds());
    }

    @Override
    @Transactional
    public void deleteCourse(String id) {
        structure.deleteCourse(id);
    }

    // Lesson CRUD
    @Override
    @Transactional
    public LessonDto createLesson(LessonDto lessonDto) {
        if (lessonDto.getId() == null || lessonDto.getId().isBlank() || lessonDto.getTitle() == null
                || lessonDto.getTitle().isBlank())
            throw new BadRequestException("Mã và tên bài học không được để trống");
        Chapter chapter = structure.chapter(lessonDto.getChapterId());
        Course course = chapter.getCourse();
        authorization.edit(course);
        if ("DELETED".equals(course.getStatus()))
            throw new BadRequestException("Khóa học đã bị xóa");
        String targetLessonId = lessonDto.getId().trim();
        Lesson existing = lessonRepository.findById(targetLessonId).orElse(null);
        Lesson lesson;
        int nextOrder = chapter.getLessons().stream()
                .filter(l -> !"DELETED".equals(l.getStatus()))
                .mapToInt(l -> l.getLessonOrder() == null ? 0 : l.getLessonOrder())
                .max().orElse(-1) + 1;
        if (existing != null) {
            if (!"DELETED".equals(existing.getStatus())) {
                String basePrefix = targetLessonId.replaceAll("\\d+$", "");
                int seq = 1;
                while (lessonRepository.existsById(basePrefix + seq)) {
                    seq++;
                }
                targetLessonId = basePrefix + seq;
                lesson = Lesson.builder()
                        .id(targetLessonId)
                        .title(lessonDto.getTitle())
                        .course(course)
                        .chapter(chapter)
                        .lessonOrder(nextOrder)
                        .hint(lessonDto.getHint())
                        .status("DRAFT")
                        .build();
            } else {
                lesson = existing;
                lesson.setTitle(lessonDto.getTitle());
                lesson.setCourse(course);
                lesson.setChapter(chapter);
                lesson.setLessonOrder(nextOrder);
                lesson.setHint(lessonDto.getHint());
                lesson.setStatus("DRAFT");
            }
        } else {
            lesson = Lesson.builder()
                    .id(targetLessonId)
                    .title(lessonDto.getTitle())
                    .course(course)
                    .chapter(chapter)
                    .lessonOrder(nextOrder)
                    .hint(lessonDto.getHint())
                    .status("DRAFT")
                    .build();
        }

        lesson.ensureContent();
        assets.attach(normalizeUploadedPath(lessonDto.getVideoUrl()));
        lesson.setVideoUrl(normalizeUploadedPath(lessonDto.getVideoUrl()));
        lesson.setVideoSource(resolveVideoSource(lessonDto));
        lesson.setTextBody(LessonContentPolicy.normalizeText(lessonDto.getTextBody()));
        lesson.setDocuments(buildDocuments(lessonDto.getDocuments(), lesson));

        if (lessonDto.getProcedureSteps() != null) {
            List<ProcedureSection> sections = new ArrayList<>();
            for (int i = 0; i < lessonDto.getProcedureSteps().size(); i++) {
                ProcedureSectionDto secDto = lessonDto.getProcedureSteps().get(i);
                sections.add(ProcedureSection.builder()
                        .title(secDto.getTitle())
                        .steps(secDto.getSteps() != null ? secDto.getSteps() : new ArrayList<>())
                        .lesson(lesson)
                        .sectionOrder(i)
                        .build());
            }
            lesson.setProcedureSteps(sections);
        }

        Lesson saved = lessonRepository.saveAndFlush(lesson);
        return courseMapper.toLessonDto(saved);
    }

    @Override
    @Transactional
    public LessonDto updateLesson(String id, LessonDto lessonDto) {
        if (lessonDto.getTitle() == null || lessonDto.getTitle().isBlank())
            throw new BadRequestException("Tên bài học không được để trống");
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));

        authorization.edit(lesson.getCourse());
        if ("DELETED".equals(lesson.getCourse().getStatus()))
            throw new BadRequestException("Khóa học đã bị xóa");
        if (lessonDto.getVersion() != null && !java.util.Objects.equals(lesson.getVersion(), lessonDto.getVersion()))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Bài học đã thay đổi. Tải lại trước khi lưu");
        if (lessonDto.getChapterId() != null && !lesson.getChapter().getId().equals(lessonDto.getChapterId()))
            throw new BadRequestException("Không đổi chương trong trình soạn nội dung");
        lesson.setTitle(lessonDto.getTitle());
        lesson.setUpdatedAt(java.time.LocalDateTime.now());
        lesson.setHint(lessonDto.getHint());
        lesson.setTextBody(LessonContentPolicy.normalizeText(lessonDto.getTextBody()));
        assets.attach(normalizeUploadedPath(lessonDto.getVideoUrl()));
        lesson.setVideoUrl(normalizeUploadedPath(lessonDto.getVideoUrl()));
        lesson.setVideoSource(resolveVideoSource(lessonDto));
        var updatedDocuments = buildDocuments(lessonDto.getDocuments(), lesson);
        lesson.getDocuments().removeIf(document -> !updatedDocuments.contains(document));
        for (var document : updatedDocuments)
            if (!lesson.getDocuments().contains(document)) lesson.getDocuments().add(document);
        lesson.getDocuments().sort(java.util.Comparator.comparing(LessonDocument::getDocumentOrder));
        if ("PUBLISHED".equals(lesson.getStatus()))
            LessonContentPolicy.requirePublishable(lesson);

        // Rebuild procedure steps to avoid merge errors
        lesson.getProcedureSteps().clear();
        if (lessonDto.getProcedureSteps() != null) {
            for (int i = 0; i < lessonDto.getProcedureSteps().size(); i++) {
                ProcedureSectionDto secDto = lessonDto.getProcedureSteps().get(i);
                lesson.getProcedureSteps().add(ProcedureSection.builder()
                        .title(secDto.getTitle())
                        .steps(secDto.getSteps() != null ? secDto.getSteps() : new ArrayList<>())
                        .lesson(lesson)
                        .sectionOrder(i)
                        .build());
            }
        }

        Lesson saved = lessonRepository.saveAndFlush(lesson);
        return courseMapper.toLessonDto(saved);
    }

    private VideoSource resolveVideoSource(LessonDto lessonDto) {
        if (lessonDto.getVideoSource() != null) {
            return lessonDto.getVideoSource();
        }
        String videoUrl = lessonDto.getVideoUrl();
        return videoUrl != null && videoUrl.contains("/uploads/")
                ? VideoSource.UPLOAD
                : VideoSource.URL;
    }

    private String normalizeUploadedPath(String value) {
        if (value == null)
            return null;
        int uploadsIndex = value.indexOf("/uploads/");
        return uploadsIndex > 0 ? value.substring(uploadsIndex) : value;
    }

    @Override
    @Transactional
    public void deleteLesson(String id) {
        structure.deleteLesson(id);
    }

    @Override
    @Transactional
    public List<LessonDto> reorderLessons(String courseId, List<String> lessonIds) {
        authorization.edit(courseId);
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", "id", courseId);
        }
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByLessonOrderAscIdAsc(courseId);
        Set<String> existingIds = lessons.stream().map(Lesson::getId).collect(Collectors.toSet());
        if (lessonIds == null
                || lessonIds.size() != lessons.size()
                || Set.copyOf(lessonIds).size() != lessonIds.size()
                || !existingIds.equals(Set.copyOf(lessonIds))) {
            throw new IllegalArgumentException(
                    "Danh sách sắp xếp phải chứa đầy đủ và duy nhất các bài học của khóa học");
        }

        java.util.Map<String, Lesson> lessonsById = lessons.stream()
                .collect(Collectors.toMap(Lesson::getId, lesson -> lesson));
        for (int index = 0; index < lessonIds.size(); index++) {
            lessonsById.get(lessonIds.get(index)).setLessonOrder(index);
        }
        return courseMapper.toLessonDtoList(
                lessonRepository.saveAll(
                        lessonIds.stream().map(lessonsById::get).toList()));
    }

    private int nextLessonOrder(String courseId) {
        return lessonRepository.findByCourseIdOrderByLessonOrderAscIdAsc(courseId).stream()
                .map(Lesson::getLessonOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private void normalizeLessonOrder(String courseId) {
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByLessonOrderAscIdAsc(courseId);
        for (int index = 0; index < lessons.size(); index++) {
            lessons.get(index).setLessonOrder(index);
        }
        lessonRepository.saveAll(lessons);
    }

    private List<LessonDocument> buildDocuments(List<LessonDocumentDto> documentDtos, Lesson lesson) {
        if (documentDtos == null) {
            return new ArrayList<>();
        }
        if (documentDtos.stream().filter(d -> d != null && d.getPurpose() == DocumentPurpose.SLIDE).count() > 1)
            throw new BadRequestException("Mỗi bài học chỉ được có một file slide PowerPoint");

        List<LessonDocument> documents = new ArrayList<>();
        var existing = lesson.getDocuments().stream().filter(d -> d.getId() != null)
                .collect(Collectors.toMap(LessonDocument::getId, d -> d));
        var seen = new java.util.HashSet<Long>();
        for (int i = 0; i < documentDtos.size(); i++) {
            LessonDocumentDto documentDto = documentDtos.get(i);
            if (documentDto == null || documentDto.getName() == null || documentDto.getName().isBlank()
                    || documentDto.getFileUrl() == null || documentDto.getFileUrl().isBlank()
                    || documentDto.getDocumentType() == null)
                throw new BadRequestException("Tài liệu cần tên, file và định dạng hợp lệ");
            String path = normalizeUploadedPath(documentDto.getFileUrl());
            var purpose = documentDto.getPurpose() == null ? DocumentPurpose.SUPPORTING : documentDto.getPurpose();
            if (purpose == DocumentPurpose.SLIDE && (documentDto.getDocumentType() != DocumentType.POWERPOINT
                    || !path.startsWith("/uploads/documents/")
                    || !path.toLowerCase(java.util.Locale.ROOT).endsWith(".pptx")))
                throw new BadRequestException("Nội dung slide cần file PowerPoint .pptx để học viên xem và ghi nhận tiến độ");
            LessonDocument previous = documentDto.getId() == null ? null : existing.get(documentDto.getId());
            if (documentDto.getId() != null && (previous == null || !seen.add(documentDto.getId())))
                throw new BadRequestException("ID tài liệu không thuộc bài học hoặc bị trùng");
            assets.attach(path);
            boolean sameContent = previous != null && java.util.Objects.equals(previous.getFileUrl(), path)
                    && previous.getDocumentType() == documentDto.getDocumentType()
                    && previous.getPurpose() == purpose;
            // A new file or a supporting -> slide promotion must not inherit a
            // bookmark's completion from the old document identity.
            var document = sameContent ? previous : LessonDocument.builder().lesson(lesson).build();
            document.setName(documentDto.getName().trim());
            document.setDocumentType(documentDto.getDocumentType());
            document.setPurpose(purpose);
            document.setFileUrl(path);
            document.setOriginalFileName(documentDto.getOriginalFileName());
            document.setFileSize(documentDto.getFileSize());
            document.setDocumentOrder(i);
            documents.add(document);
        }
        return documents;
    }

    @Override
    public LessonDocumentDto uploadDocument(MultipartFile file) {
        LessonDocumentDto document = documentStorage.store(file);
        assets.record(document.getFileUrl());
        return document;
    }

    @Override
    public UploadResultDto.Video uploadVideo(MultipartFile file) {
        String path = videoStorage.store(file);
        assets.record(path);
        return new UploadResultDto.Video(path);
    }
}
