package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.Comment;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.ProcedureSection;
import com.bidv.coretraining.entity.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CourseMapper {

    TopicDto toTopicDto(Topic topic);

    List<TopicDto> toTopicDtoList(List<Topic> topics);

    @Mapping(source = "topic.id", target = "topicId")
    LessonDto toLessonDto(Lesson lesson);

    List<LessonDto> toLessonDtoList(List<Lesson> lessons);

    ProcedureSectionDto toProcedureSectionDto(ProcedureSection section);

    List<ProcedureSectionDto> toProcedureSectionDtoList(List<ProcedureSection> sections);

    @Mapping(source = "username", target = "user")
    @Mapping(source = "createdAt", target = "date", dateFormat = "yyyy-MM-dd")
    CommentDto toCommentDto(Comment comment);

    List<CommentDto> toCommentDtoList(List<Comment> comments);
}
