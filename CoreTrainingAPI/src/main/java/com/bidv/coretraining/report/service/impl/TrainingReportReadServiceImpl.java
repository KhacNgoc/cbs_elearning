package com.bidv.coretraining.report.service.impl;
import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.quiz.entity.UserQuizScore;
import com.bidv.coretraining.quiz.repository.QuestionRepository;
import com.bidv.coretraining.quiz.repository.QuizAttemptRepository;
import com.bidv.coretraining.quiz.repository.UserQuizScoreRepository;
import com.bidv.coretraining.report.dto.TrainingReportDto.AttemptAnalysis;
import com.bidv.coretraining.report.dto.TrainingReportQueryDto.Filter;
import com.bidv.coretraining.report.repository.TrainingReportQueryRepository.*;
import com.bidv.coretraining.report.repository.TrainingReportQueryRepository;
import com.bidv.coretraining.report.service.TrainingReportReadService;
import com.bidv.coretraining.report.service.ReportScopeResolver;
import com.bidv.coretraining.security.ReportPages;
import com.bidv.coretraining.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
@Transactional(readOnly=true)
public class TrainingReportReadServiceImpl implements TrainingReportReadService {
    private final AuthorizationService auth;
    private final TrainingReportQueryRepository repository;
    private final UserQuizScoreRepository scores;
    private final QuizAttemptRepository quizAttempts;
    private final QuestionRepository questions;
    private final ReportScopeResolver scopes;
    public TrainingReportReadServiceImpl(AuthorizationService auth, TrainingReportQueryRepository repository, UserQuizScoreRepository scores, QuizAttemptRepository quizAttempts, QuestionRepository questions, ReportScopeResolver scopes) {this.auth=auth;this.repository=repository;this.scores=scores;this.quizAttempts=quizAttempts;this.questions=questions;this.scopes=scopes;}
    private Filter filter(Map<String,String> p) {
        try { return new Filter(p.get("from"),p.get("to"),p.get("unit"),p.get("program"),p.get("course"),p.get("status"),p.getOrDefault("view","completion"),p.get("action"),p.get("search"),Integer.parseInt(p.getOrDefault("page","0")),Integer.parseInt(p.getOrDefault("size","20")),p.get("dimension"),p.get("key")); }
        catch(NumberFormatException e) { throw new BadRequestException("Tham số phân trang không hợp lệ"); }
    }
    private void validate(Filter f) {
        LocalDate today=LocalDate.now();
        LocalDate from=f.from()==null || f.from().isBlank() ? null : LocalDate.parse(f.from());
        if(from!=null && from.isAfter(today)) throw new BadRequestException("Khoảng ngày không hợp lệ");
        LocalDate to=f.to()==null || f.to().isBlank() ? null : LocalDate.parse(f.to());
        if(to!=null && (to.isAfter(today) || from!=null && from.isAfter(to))) throw new BadRequestException("Khoảng ngày không hợp lệ");
    }
    private User actor() {auth.requireOperation("reports","read");return auth.current();}
    /**
     * The report pages that admit a read of this view: the view's own page, and for a drill-down into one group
     * (dimension and key) also the page of that group's view and the dashboard, which open such drill-downs.
     */
    private static Set<String> accepted(Filter f) {
        Set<String> pages=new HashSet<>();
        pages.add(ReportPages.pageOf(f.view())+":read");
        if(f.dimension()!=null && !f.dimension().isBlank() && f.key()!=null && !f.key().isBlank()) {
            pages.add(ReportPages.pageOf(f.dimension())+":read");
            pages.add(ReportPages.OVERVIEW+":read");
        }
        return pages;
    }
    /** Learners within the units of the roles that grant this view's page; other roles do not widen it. */
    private Scope scope(User actor,Filter f) {
        var pages=accepted(f);
        ReportScopeResolver.require(actor,pages);
        return scopes.resolve(actor,pages);
    }
    /** Attempts open from the detail rows of every report view, so any report page reads them within its scope. */
    private Scope anyReportScope(User actor) {return scopes.resolve(actor,ReportPages.READ);}
    public Map<String,Object> read(Map<String,String> params) {
        var actor=actor(); var f=filter(params); var scope=scope(actor,f); validate(f);
        return repository.read(scope,f,!"false".equals(params.get("metadata")));
    }
    public Map<String,Object> read(Filter f) {return read(f,true);}
    public Map<String,Object> read(Filter f,boolean metadata) {var scope=scope(actor(),f);validate(f);return repository.read(scope,f,metadata);}
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public void export(Map<String,String> params,java.io.Writer writer) throws java.io.IOException {
        var actor=actor(); var f=filter(params); var scope=scope(actor,f);validate(f);export(scope,f,writer);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public void export(Filter f,java.io.Writer writer) throws java.io.IOException {var scope=scope(actor(),f);validate(f);export(scope,f,writer);}
    private void export(Scope scope,Filter f,java.io.Writer writer) throws java.io.IOException {
        writer.write('\ufeff');csvLine(writer,csvHeaders(f.view()));
        repository.export(scope,f,row->{try {csvLine(writer,csvCells(row,f.view()));} catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}});
        writer.flush();
    }
    private static List<?> csvHeaders(String view) {
        if ("scores".equals(view))
            return List.of("Học viên", "Khóa học", "Số lần thi", "TB điểm gần nhất /100", "Kết quả qua các lần thi");
        if ("domains".equals(view))
            return List.of("Lĩnh vực", "Học viên", "Lượt học", "Tỷ lệ hoàn thành", "Đạt kiểm tra / đã thi",
                    "Điểm kiểm tra TB /100");
        if ("learners".equals(view))
            return List.of("Học viên", "Khóa được giao / tham gia", "Hoàn thành", "Tỷ lệ hoàn thành",
                    "Đạt kiểm tra / đã thi", "Điểm TB /100");
        if ("programs".equals(view))
            return List.of("Chương trình", "Học viên", "Khóa bắt buộc đã giao", "Bắt buộc hoàn thành",
                    "Tiến độ chương trình", "Đạt kiểm tra / đã thi");
        if ("courses".equals(view))
            return List.of("Khóa học", "Ghi danh", "Hoàn thành", "Tỷ lệ hoàn thành", "Đạt kiểm tra / đã thi",
                    "Điểm TB /100", "Bỏ dở");
        if ("units".equals(view))
            return List.of("Đơn vị", "Học viên", "Lượt học", "Tỷ lệ hoàn thành", "Tỷ lệ đạt kiểm tra",
                    "Học viên quá hạn / có hạn");
        return List.of("Học viên", "Đơn vị", "Khóa học", "Trạng thái", "Hạn hoàn thành", "Kết quả kiểm tra");
    }

    private static long number(Map<String, Object> r, String name) {
        return r.get(name) instanceof Number n ? n.longValue() : 0;
    }

    private static String ratio(long n, long d) {
        return d == 0 ? "—" : Math.round(n * 100.0 / d) + "%";
    }

    private static List<?> csvCells(Map<String, Object> r, String view) {
        String label = Objects.toString(r.get("name"), ""), avg = r.get("average") == null ? "Chưa đánh giá"
                : String.format(Locale.ROOT, "%.1f", ((Number) r.get("average")).doubleValue());
        long total = number(r, "total"), complete = number(r, "complete"), mandatory = number(r, "mandatory"),
                mandatoryComplete = number(r, "mandatoryComplete"), tested = number(r, "tested"),
                passed = number(r, "passed");
        String pass = passed + " / " + tested + " (" + ratio(passed, tested) + ")";
        return switch (view == null ? "" : view) {
            case "domains" -> List.of(label, number(r, "people"), total, ratio(complete, total), pass, avg);
            case "learners" -> List.of(r.get("learnerName") + " · " + label, mandatory + " / " + total, complete,
                    ratio(complete, total), pass, avg);
            case "programs" -> List.of(label, number(r, "people"), mandatory, mandatoryComplete,
                    ratio(mandatoryComplete, mandatory), pass);
            case "courses" -> List.of(label, total, complete, ratio(complete, total), pass, avg, "Chưa có dữ liệu");
            case "units" ->
                List.of(label, number(r, "people"), total, ratio(complete, total), pass,
                        number(r, "overduePeople") + " / " + number(r, "peopleWithDeadline") + " ("
                                + ratio(number(r, "overduePeople"), number(r, "peopleWithDeadline")) + ")");
            case "scores" ->
                List.of(label + " · " + r.get("learnerId"), r.get("courseName"), number(r, "attemptCount"), avg,
                        number(r, "attemptCount") == 0 ? "Chưa kiểm tra"
                                : number(r, "everPassed") == 1 && number(r, "requiredPassed") == 1 ? "Đã từng đạt"
                                        // Passed every quiz taken; only an untaken required quiz is missing.
                                        : number(r, "everPassed") == 1 ? "Đạt bài đã làm, còn bài bắt buộc chưa làm"
                                        : "Chưa từng đạt");
            default -> List.of(label + " · " + r.get("learnerId"), r.get("unit"),
                    r.get("courseName"), r.get("status"), Objects.toString(r.get("dueDate"), "Chưa quy định"), avg);
        };
    }

    private static void csvLine(java.io.Writer writer, List<?> cells) throws java.io.IOException {
        boolean first = true;
        for (Object cell : cells) {
            if (!first)
                writer.write(',');
            first = false;
            String value = Objects.toString(cell, "");
            if (value.matches("(?s)^\\s*[=+@-].*") || value.startsWith("\t") || value.startsWith("\r")
                    || value.startsWith("\n"))
                value = "'" + value;
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    public Map<String,Object> attempts(String learner,String course,String before,int size) {
        var scope=anyReportScope(actor()); long learnerId=Long.valueOf(learner);
        String context = UUID
                .nameUUIDFromBytes((learner + "\\0" + course).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        AttemptBoundary boundary = null;
        if (before != null && !before.isBlank()) {
            try {
                String[] key = new String(Base64.getUrlDecoder().decode(before),
                        java.nio.charset.StandardCharsets.UTF_8).split("\\|", -1);
                if (key.length != 3 || !key[0].equals(context))
                    throw new IllegalArgumentException();
                boundary = new AttemptBoundary(LocalDateTime.parse(key[1]),key[2]);
            } catch (RuntimeException e) {
                throw new com.bidv.coretraining.exception.BadRequestException("Cursor không hợp lệ");
            }
        }
        int limit=Math.max(1,Math.min(size,100));
        var rows=repository.attempts(scope,learnerId,course,boundary,limit);
        boolean more = rows.size() > limit;
        var items = new ArrayList<>(rows.subList(0, Math.min(limit, rows.size())));
        String cursor = "";
        if (more) {
            var last = items.get(items.size() - 1);
            cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (context + "|" + last.get("takenAt").toString().replace(' ', 'T') + "|" + last.get("id"))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return Map.of("items", items, "hasMore", more, "nextCursor", cursor);
    }

    @Override
    public AttemptAnalysis attemptAnalysis(String attemptId) {
        if(!repository.attemptVisible(anyReportScope(actor()),attemptId))
            throw new ResourceNotFoundException("Quiz attempt","id",attemptId);
        var attempt=quizAttempts.findById(attemptId);
        if(attempt.isPresent())
            return AttemptAnalysisAssembler.fromAttempt(attempt.get());
        UserQuizScore score=scores.findById(Long.valueOf(attemptId))
                .orElseThrow(()->new ResourceNotFoundException("Quiz attempt","id",attemptId));
        if(score.getLesson()==null)
            return AttemptAnalysisAssembler.fromCurrentQuestions(score,questions.findByCourseIdAndLessonIsNullOrderByQuestionOrderAscIdAsc(score.getCourse().getId()));
        String lessonId=score.getLesson().getId();
        // Submission writes the score first and its snapshot moments later in the same transaction.
        return quizAttempts.findRecordedSnapshots(score.getUser().getId(),lessonId,score.getScore(),score.getTotal(),score.getTakenAt(),score.getTakenAt().plusMinutes(1)).stream()
                .flatMap(a->AttemptAnalysisAssembler.fromSnapshot(attemptId,score.getScore(),score.getTotal(),a.getQuestionsJson(),a.getAnswersJson()).stream())
                .findFirst()
                .orElseGet(()->AttemptAnalysisAssembler.fromCurrentQuestions(score,questions.findByLessonIdOrderByQuestionOrderAscIdAsc(lessonId)));
    }
}
