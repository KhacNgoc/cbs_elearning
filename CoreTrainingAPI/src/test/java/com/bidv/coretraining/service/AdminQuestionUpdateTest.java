package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.impl.AdminServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.config.location=classpath:access-test.yml", "spring.sql.init.mode=never"})
class AdminQuestionUpdateTest {
    @Autowired EntityManager em;
    @Autowired CourseRepository courses;
    @Autowired LessonRepository lessons;
    @Autowired QuestionRepository questions;

    @ParameterizedTest
    @EnumSource(QuestionType.class)
    void editingExistingQuestionPersistsNormalizedAnswers(QuestionType type) {
        var domain = TrainingDomain.builder().code("D").name("Domain").build();
        em.persist(domain);
        var program = TrainingProgram.builder().code("P").name("Program").domain(domain).build();
        em.persist(program);
        var course = Course.builder().id("C").code("C").name("Course").program(program).build();
        em.persist(course);
        var chapter = Chapter.builder().id("CH").code("CH").title("Chapter").course(course).build();
        em.persist(chapter);
        var lesson = Lesson.builder().id("L").title("Lesson").course(course).chapter(chapter).build();
        lesson.ensureQuiz();
        em.persist(lesson);
        var question = Question.builder().course(course).lesson(lesson).quiz(lesson.getQuiz())
                .text("Original").options(new ArrayList<>(List.of("A", "B", "C", "D")))
                .correctAnswer(0).correctAnswers(new ArrayList<>(List.of(0))).build();
        em.persist(question);
        em.flush();
        var id = question.getId();
        em.clear();

        var service = new AdminServiceImpl(courses, lessons, questions, mock(CourseMapper.class),
                mock(QuizMapper.class), mock(UserRepository.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(CourseEnrollmentRequestRepository.class), mock(NotificationService.class), mock(UserGroupRepository.class));
        ReflectionTestUtils.setField(service, "authorization", mock(AuthorizationService.class));
        var answers = type == QuestionType.SINGLE_CHOICE ? List.of(2) : List.of(2, 0);
        var request = QuestionDto.builder().courseId("C").lessonId("L").text("Edited")
                .options(List.of(" New A ", "New B", "New C", "New D"))
                .type(type).correctAnswers(answers).build();

        service.updateQuestion(id, request);
        em.flush();
        em.clear();
        var updated = questions.findById(id).orElseThrow();
        assertThat(updated.getText()).isEqualTo("Edited");
        assertThat(updated.getOptions()).containsExactly("New A", "New B", "New C", "New D");
        assertThat(updated.getCorrectAnswers()).containsExactlyElementsOf(answers.stream().sorted().toList());
        assertThat(updated.getType()).isEqualTo(type);
        assertThat(updated.getLesson().getId()).isEqualTo("L");
    }
}
