package com.bidv.coretraining.policy;

import com.bidv.coretraining.dto.QuestionDto;
import com.bidv.coretraining.enums.QuestionType;
import com.bidv.coretraining.exception.BadRequestException;
import java.util.*;

public final class QuestionValidation {
    private QuestionValidation() {
    }

    public static void normalize(QuestionDto q) {
        if (q.getText() == null || q.getText().isBlank() || q.getText().length() > 20000)
            throw new BadRequestException("Nội dung câu hỏi phải từ 1 đến 20.000 ký tự");
        List<String> options = q.getOptions() == null ? List.of()
                : q.getOptions().stream().map(s -> s == null ? "" : s.trim()).toList();
        if (options.size() < 3 || options.size() > 6
                || options.stream().anyMatch(s -> s.isBlank() || s.length() > 5000))
            throw new BadRequestException("Câu hỏi cần 3 đến 6 lựa chọn hợp lệ, tối đa 5.000 ký tự mỗi lựa chọn");
        if (options.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count() != options.size())
            throw new BadRequestException("Các lựa chọn không được trùng nhau");
        List<Integer> answers = q.getCorrectAnswers();
        if ((answers == null || answers.isEmpty()) && q.getCorrectAnswer() != null)
            answers = List.of(q.getCorrectAnswer());
        answers = answers == null ? List.of() : answers.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (answers.isEmpty() || answers.stream().anyMatch(i -> i < 0 || i >= options.size()))
            throw new BadRequestException("Đáp án đúng không hợp lệ");
        QuestionType type = q.getType() == null
                ? (answers.size() > 1 ? QuestionType.MULTIPLE_CHOICE : QuestionType.SINGLE_CHOICE)
                : q.getType();
        if (type == QuestionType.SINGLE_CHOICE && answers.size() != 1)
            throw new BadRequestException("Câu một lựa chọn cần đúng một đáp án");
        if (type == QuestionType.MULTIPLE_CHOICE && (answers.size() < 2 || answers.size() >= options.size()))
            throw new BadRequestException("Câu nhiều lựa chọn cần ít nhất hai đáp án đúng và không chọn tất cả");
        if (q.getExplanation() != null && q.getExplanation().length() > 20000)
            throw new BadRequestException("Giải thích tối đa 20.000 ký tự");
        q.setText(q.getText().trim());
        q.setOptions(options);
        q.setCorrectAnswers(answers);
        q.setCorrectAnswer(answers.get(0));
        q.setType(type);
    }
}
