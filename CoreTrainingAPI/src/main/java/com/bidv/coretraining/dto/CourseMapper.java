package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.Comment;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.LessonDocument;
import com.bidv.coretraining.entity.ProcedureSection;
import com.bidv.coretraining.entity.Topic;
import com.bidv.coretraining.entity.VideoSource;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;
import java.util.Comparator;

@Mapper(componentModel = "spring")
public interface CourseMapper {

    @Mapping(
            target = "hasQuiz",
            expression = "java(topic.getQuestions() != null && topic.getQuestions().stream().anyMatch(question -> question.getLesson() == null))"
    )
    TopicDto toTopicDto(Topic topic);

    List<TopicDto> toTopicDtoList(List<Topic> topics);

    @Mapping(source = "topic.id", target = "topicId")
    @Mapping(
            target = "hasQuiz",
            expression = "java(lesson.getQuizQuestions() != null && !lesson.getQuizQuestions().isEmpty())"
    )
    @Mapping(target = "comments", qualifiedByName = "rootComments")
    LessonDto toLessonDto(Lesson lesson);

    @AfterMapping
    default void inferLegacyVideoSource(Lesson lesson, @MappingTarget LessonDto dto) {
        if (dto.getVideoSource() == null) {
            String videoUrl = lesson.getVideoUrl();
            dto.setVideoSource(videoUrl != null && videoUrl.contains("/uploads/")
                    ? VideoSource.UPLOAD
                    : VideoSource.URL);
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
    CommentDto toCommentDto(Comment comment);

    @Named("allComments")
    List<CommentDto> toCommentDtoList(List<Comment> comments);

    @Named("rootComments")
    default List<CommentDto> toRootCommentDtoList(List<Comment> comments) {
        if (comments == null) return List.of();
        return comments.stream()
                .filter(comment -> comment.getParent() == null)
                .sorted(Comparator.comparing(Comment::getCreatedAt).reversed())
                .map(this::toCommentDto)
                .toList();
    }
}
