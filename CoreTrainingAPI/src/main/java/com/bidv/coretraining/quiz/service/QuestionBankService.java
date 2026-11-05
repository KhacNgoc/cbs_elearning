package com.bidv.coretraining.quiz.service;

import com.bidv.coretraining.quiz.dto.BankQuestionDto;
import com.bidv.coretraining.quiz.dto.QuestionDto;
import java.util.*;

/** Business operations for QuestionBankService. */
public interface QuestionBankService {
    public List<BankQuestionDto> list();

    public BankQuestionDto save(Long id, BankQuestionDto request);

    public BankQuestionDto capture(Long questionId);

    public List<QuestionDto> addToQuiz(String lessonId, List<Long> ids);
}
