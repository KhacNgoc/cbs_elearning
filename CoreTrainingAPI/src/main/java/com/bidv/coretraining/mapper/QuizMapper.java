package com.bidv.coretraining.mapper;
import com.bidv.coretraining.quiz.dto.QuestionDto;

import com.bidv.coretraining.quiz.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuizMapper {

    @Mapping(source = "course.id", target = "courseId")
    @Mapping(source = "lesson.id", target = "lessonId")
    QuestionDto toQuestionDto(Question question);

    List<QuestionDto> toQuestionDtoList(List<Question> questions);
}
