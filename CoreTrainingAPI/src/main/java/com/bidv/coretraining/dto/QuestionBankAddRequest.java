package com.bidv.coretraining.dto;

import java.util.List;

public record QuestionBankAddRequest(String lessonId, List<Long> questionIds) {
}
