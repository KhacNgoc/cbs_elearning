package com.bidv.coretraining.quiz.dto;

import java.util.List;

public record QuestionBankAddRequest(String lessonId, List<Long> questionIds) {
}
