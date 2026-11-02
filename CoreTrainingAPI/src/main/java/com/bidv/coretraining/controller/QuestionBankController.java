package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.service.QuestionBankService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/question-bank")
public class QuestionBankController {
    private final QuestionBankService bank;

    public QuestionBankController(QuestionBankService bank) {
        this.bank = bank;
    }

    @GetMapping
    public List<BankQuestionDto> list() {
        return bank.list();
    }

    @PostMapping
    public BankQuestionDto create(@RequestBody BankQuestionDto request) {
        return bank.save(null, request);
    }

    @PutMapping("/{id}")
    public BankQuestionDto update(@PathVariable Long id, @RequestBody BankQuestionDto request) {
        return bank.save(id, request);
    }

    @PostMapping("/capture/{questionId}")
    public BankQuestionDto capture(@PathVariable Long questionId) {
        return bank.capture(questionId);
    }

    @PostMapping("/add-to-quiz")
    public List<QuestionDto> add(@RequestBody QuestionBankAddRequest request) {
        return bank.addToQuiz(request.lessonId(), request.questionIds());
    }
}
