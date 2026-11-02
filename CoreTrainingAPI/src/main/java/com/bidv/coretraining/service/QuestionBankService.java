package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;
import java.util.*;

/** Business operations for QuestionBankService. */
public interface QuestionBankService {
    public List<BankQuestionDto> list();

    public BankQuestionDto save(Long id, BankQuestionDto request);

    public BankQuestionDto capture(Long questionId);

    public List<QuestionDto> addToQuiz(String lessonId, List<Long> ids);
}
