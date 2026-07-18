package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuizMapper {

    @Mapping(source = "topic.id", target = "topicId")
    QuestionDto toQuestionDto(Question question);

    List<QuestionDto> toQuestionDtoList(List<Question> questions);
}
