package com.bidv.coretraining.mapper;
import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.document.dto.LessonDocumentDto;
import com.bidv.coretraining.lesson.dto.CommentDto;
import com.bidv.coretraining.lesson.dto.LessonDto;
import com.bidv.coretraining.lesson.dto.ProcedureSectionDto;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.document.entity.LessonDocument;
import com.bidv.coretraining.enums.VideoSource;
import com.bidv.coretraining.lesson.entity.Comment;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.entity.ProcedureSection;
import org.mapstruct.AfterMapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;
import java.util.Comparator;

@Mapper(componentModel = "spring")
public interface CourseMapper {

        // Authorization fields are populated by the service for the current user.
        @Mapping(target = "allowedActions", ignore = true)
        @Mapping(target = "adminActions", ignore = true)
        @Mapping(target = "canEdit", ignore = true)
        // Populated explicitly by mapCourseUnitGrants on the DTO builder.
        @Mapping(target = "mandatoryUnitIds", ignore = true)
        @Mapping(target = "optionalUnitIds", ignore = true)
        @Mapping(target = "unitIds", ignore = true)
        @Mapping(target = "hasQuiz", expression = "java(course.getQuestions() != null && course.getQuestions().stream().anyMatch(question -> question.getLesson() == null))")
        @Mapping(source = "program.id", target = "programId")
        @Mapping(source = "program.name", target = "programName")
        @Mapping(source = "program.domain.name", target = "domainName")
        @Mapping(source = "program.domain.id", target = "domainId")
        CourseDto toCourseDto(Course course);

        List<CourseDto> toCourseDtoList(List<Course> courses);

        @AfterMapping
        default void mapCourseUnitGrants(Course course, @MappingTarget CourseDto.CourseDtoBuilder dto) {
                java.util.Set<String> mandatory = new java.util.HashSet<>();
                java.util.Set<String> optional = new java.util.HashSet<>();
                if (course.getUnitGrants() != null) {
                        for (var entry : course.getUnitGrants().entrySet()) {
                                if ("MANDATORY".equalsIgnoreCase(entry.getValue())) {
                                        mandatory.add(entry.getKey());
                                } else {
                                        optional.add(entry.getKey());
                                }
                        }
                }
                dto.mandatoryUnitIds(mandatory);
                dto.optionalUnitIds(optional);
                java.util.Set<String> allUnits = new java.util.HashSet<>(mandatory);
                allUnits.addAll(optional);
                dto.unitIds(allUnits);
        }

        @AfterMapping
        default void chapterOrder(Course course, @MappingTarget CourseDto dto) {
                dto.setLessons(toLessonDtoList(course.getChapters().stream()
                                .sorted(Comparator.comparing(com.bidv.coretraining.course.entity.Chapter::getChapterOrder))
                                .flatMap(chapter -> chapter.getLessons().stream()
                                                .sorted(Comparator.comparing(Lesson::getLessonOrder)))
                                .toList()));
        }

        @Mapping(source = "course.id", target = "courseId")
        @Mapping(source = "chapter.id", target = "chapterId")
        @Mapping(target = "quizRequired", expression = "java(lesson.getQuiz() != null && lesson.getQuiz().isRequiredForProgress())")
        @Mapping(target = "hasQuiz", expression = "java(lesson.hasQuiz())")
        @Mapping(target = "comments", qualifiedByName = "rootComments")
        // The detail service supplies the total from its paginated comments query.
        @Mapping(target = "commentCount", ignore = true)
        LessonDto toLessonDto(Lesson lesson);

        @InheritConfiguration(name = "toLessonDto")
        @Mapping(target = "comments", ignore = true)
        @Mapping(target = "hasQuiz", ignore = true)
        @Named("lessonDetail")
        LessonDto toLessonDetailDto(Lesson lesson);

        @AfterMapping
        default void inferLegacyVideoSource(Lesson lesson, @MappingTarget LessonDto dto) {
                if (dto.getVideoSource() == null) {
                        String videoUrl = lesson.getVideoUrl();
                        dto.setVideoSource(videoUrl != null && videoUrl.contains("/uploads/")
                                        ? VideoSource.UPLOAD
                                        : VideoSource.URL);
                }
                if (dto.getHasText() == null) {
                        dto.setHasText(lesson.getTextBody() != null && !lesson.getTextBody().trim().isEmpty());
                }
                if (dto.getHasVideo() == null) {
                        dto.setHasVideo(lesson.getVideoUrl() != null && !lesson.getVideoUrl().trim().isEmpty());
                }
        }

        List<LessonDto> toLessonDtoList(List<Lesson> lessons);

        LessonDocumentDto toLessonDocumentDto(LessonDocument document);

        List<LessonDocumentDto> toLessonDocumentDtoList(List<LessonDocument> documents);

        ProcedureSectionDto toProcedureSectionDto(ProcedureSection section);

        List<ProcedureSectionDto> toProcedureSectionDtoList(List<ProcedureSection> sections);

        @Mapping(source = "username", target = "user")
        @Mapping(source = "createdAt", target = "date", dateFormat = "yyyy-MM-dd")
        @Mapping(source = "parent.id", target = "parentId")
        @Mapping(source = "replyToUsername", target = "replyToUser")
        @Mapping(target = "replies", qualifiedByName = "allComments")
        @Mapping(target = "repliesHasMore", ignore = true)
        @Mapping(target = "replyCursor", ignore = true)
        CommentDto toCommentDto(Comment comment);

        @InheritConfiguration(name = "toCommentDto")
        @Mapping(target = "replies", ignore = true)
        @Named("commentSummary")
        CommentDto toCommentSummaryDto(Comment comment);

        @Named("allComments")
        List<CommentDto> toCommentDtoList(List<Comment> comments);

        @Named("rootComments")
        default List<CommentDto> toRootCommentDtoList(List<Comment> comments) {
                if (comments == null)
                        return List.of();
                return comments.stream()
                                .filter(comment -> comment.getParent() == null)
                                .sorted(Comparator.comparing(Comment::getCreatedAt).reversed())
                                .map(this::toCommentDto)
                                .toList();
        }
}
