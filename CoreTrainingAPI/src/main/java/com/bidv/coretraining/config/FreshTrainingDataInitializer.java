package com.bidv.coretraining.config;
import com.bidv.coretraining.enums.QuestionType;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Fresh v3 data; no migration from the former Topic hierarchy. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.data-initializer", name = "enabled", havingValue = "true")
public class FreshTrainingDataInitializer implements CommandLineRunner {
        private final TrainingDomainRepository domains;
        private final TrainingProgramRepository programs;
        private final CourseRepository courses;
        private final ChapterRepository chapters;
        private final LessonRepository lessons;
        private final QuestionRepository questions;
        private final UserRepository users;
        private final PasswordEncoder passwords;
        @org.springframework.beans.factory.annotation.Value("${app.data-initializer.admin-password:123@qwe}")
        private String initialPassword;

        @Override
        @Transactional
        public void run(String... args) {
                if (courses.count() > 0)
                        return;
                TrainingDomain domain = domains.save(TrainingDomain.builder().code("TD").name("Tín dụng").build());
                TrainingProgram program = programs
                                .save(TrainingProgram.builder().domain(domain).code("CT-LN").name("Nghiệp vụ cho vay")
                                                .build());
                Course course = courses
                                .save(Course.builder().id("LN-CB").code("LN-CB").name("LN cơ bản").program(program)
                                                .iconName("Coins")
                                                .description("Kiến thức nền tảng và thao tác phân hệ cho vay")
                                                .status("PUBLISHED").build());
                String[][] titles = {
                                { "Tổng quan phân hệ cho vay", "Quy trình giải ngân khoản vay" },
                                { "Thực hành tạo hồ sơ khoản vay",
                                                "Kiểm tra hồ sơ", "Phê duyệt khoản vay", "Giải ngân và đối soát",
                                                "Theo dõi khoản vay" },
                                { "Tổng kết khóa học" } };
                String[] chapterTitles = { "Tổng quan phân hệ cho vay", "Thực hành nghiệp vụ", "Tổng kết khóa học" };
                int lessonNo = 1;
                for (int i = 0; i < titles.length; i++) {
                        Chapter chapter = chapters.save(Chapter.builder().course(course).code("CH0" + (i + 1))
                                        .title(chapterTitles[i]).chapterOrder(i).status("PUBLISHED").build());
                        for (int j = 0; j < titles[i].length; j++) {
                                Lesson lesson = Lesson.builder().id(String.format("LN%02d", lessonNo))
                                                .title(titles[i][j])
                                                .course(course).chapter(chapter).lessonOrder(j).status("PUBLISHED")
                                                .hint("WebCSR > Cho vay > " + titles[i][j]).build();
                                lesson.ensureContent();
                                lesson.setTextBody(titles[i][j]
                                                + "\n\nKiểm tra thông tin khách hàng, hồ sơ và thẩm quyền trước khi ghi nhận giao dịch trên WebCSR.");
                                lesson.setProcedureSteps(new ArrayList<>(
                                                List.of(ProcedureSection.builder().lesson(lesson).sectionOrder(0)
                                                                .title("Quy trình thực hiện")
                                                                .steps(new ArrayList<>(List.of(
                                                                                "Kiểm tra thông tin khách hàng và hồ sơ.",
                                                                                "Thực hiện thao tác trên WebCSR.",
                                                                                "Đối chiếu kết quả và gửi kiểm soát viên.")))
                                                                .build())));
                                if (lessonNo <= 3 || lessonNo == 8)
                                        lesson.ensureQuiz();
                                if (lessonNo == 3)
                                        lesson.getQuiz().setRequiredForProgress(false);
                                lessons.saveAndFlush(lesson);
                                if (lesson.getQuiz() != null) {
                                        Question q = questions.save(Question.builder().course(course).lesson(lesson)
                                                        .quiz(lesson.getQuiz())
                                                        .text("CIF được sử dụng để xác định thông tin nào?")
                                                        .options(new ArrayList<>(List.of("Thông tin khách hàng",
                                                                        "Lịch giải ngân",
                                                                        "Số hiệu chứng từ", "Lãi suất sản phẩm")))
                                                        .type(QuestionType.SINGLE_CHOICE).correctAnswer(0)
                                                        .correctAnswers(new ArrayList<>(List.of(0))).build());
                                        lesson.getQuiz().getQuestions().add(q);
                                }
                                chapter.getLessons().add(lesson);
                                course.getLessons().add(lesson);
                                lessonNo++;
                        }
                        course.getChapters().add(chapter);
                }
                Course draft = courses.save(Course.builder().id("LN-GN").code("LN-GN").name("Quy trình giải ngân")
                                .program(program).iconName("BookOpen").description("Khóa nháp để soạn nội dung")
                                .build());
                chapters.save(Chapter.builder().course(draft).code("GN01").title("Quy trình tại quầy").build());
                if (!users.existsByUsername("admin"))
                        users.save(User.builder().username("admin").password(passwords.encode(initialPassword))
                                        .fullName("Quản trị hệ thống").role("ROLE_ADMIN").avatarColor("#005B41")
                                        .mustChangePassword(false)
                                        .build());
                if (!users.existsByUsername("quantrh"))
                        users.save(User.builder().username("quantrh").password(passwords.encode(initialPassword))
                                        .fullName("Học viên nghiệp vụ").role("ROLE_USER").avatarColor("#005B41")
                                        .mustChangePassword(false)
                                        .registeredCourses(new HashSet<>(Set.of(course))).build());
        }
}
