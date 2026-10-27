package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.dto.LessonReportDto;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class LessonReportService {
    private final LessonReportRepository reports;
    private final LessonRepository lessons;
    private final UserRepository users;
    private final AuthorizationService auth;
    private final NotificationService notifications;
    private final Path storage;

    public LessonReportService(LessonReportRepository reports, LessonRepository lessons, UserRepository users,
            AuthorizationService auth, NotificationService notifications,
            @Value("${app.storage.documents-directory:uploads/documents}") String directory) {
        this.reports = reports;
        this.lessons = lessons;
        this.users = users;
        this.auth = auth;
        this.notifications = notifications;
        storage = Path.of(directory).toAbsolutePath().normalize().resolve("lesson-reports");
    }

    private boolean canRead(User actor, LessonReport r) {
        return actor.activeAccount() && AccessPolicy.canReadCourse(actor, r.getLesson().getCourse());
    }

    private LessonReport report(Long id) {
        return reports.findById(id).orElseThrow(() -> new ResourceNotFoundException("LessonReport"));
    }

    public LessonReportDto submit(String lessonId, String content, List<MultipartFile> files) {
        User user = auth.current();
        AccessPolicy.require(AccessPolicy.permissions(user).contains("LEARN"));
        Lesson lesson = lessons.findById(lessonId).orElseThrow(() -> new ResourceNotFoundException("Lesson"));
        LearningPolicy.assertLessonAccess(user, lesson);
        String text = content == null ? "" : content.trim();
        if (text.isBlank() || text.length() > 5000)
            throw new BadRequestException("Nội dung báo cáo cần từ 1 đến 5.000 ký tự");
        List<MultipartFile> images = files == null ? List.of() : files;
        if (images.size() > 3)
            throw new BadRequestException("Mỗi báo cáo tối đa 3 ảnh");
        var paths = new ArrayList<Path>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED)
                    paths.forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
            }
        });
        LessonReport r = LessonReport.builder().lesson(lesson).reporter(user).content(text)
                .status(LessonReport.Status.NEW).createdAt(LocalDateTime.now()).build();
        for (MultipartFile file : images) {
            String name = UUID.randomUUID() + ".png";
            Path target = storage.resolve(name);
            paths.add(target);
            storeImage(file, target);
            r.getImages().add(name);
        }
        reports.saveAndFlush(r);
        // Notify active global administrators and editors responsible for this course.
        users.findAll().stream().filter(u -> u.activeAccount() && AccessPolicy.canReadCourse(u, lesson.getCourse()))
                .forEach(u -> notifications.create(u.getUsername(), NotificationType.LESSON_REPORT,
                        "Báo cáo nội dung bài học",
                        user.getFullName() + " đã báo cáo bài học: " + lesson.getTitle(),
                        "/admin/content-reports?report=" + r.getId()));
        return dto(r, user);
    }

    private void storeImage(MultipartFile file, Path target) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new BadRequestException("Mỗi ảnh PNG/JPEG tối đa 5 MB");
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
                throw new BadRequestException("Chỉ hỗ trợ ảnh PNG/JPEG hợp lệ");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000L)
                    throw new BadRequestException("Ảnh phải là PNG/JPEG và không quá 20 triệu điểm ảnh");
                var image = reader.read(0);
                Files.createDirectories(storage);
                ImageIO.write(image, "png", target.toFile());
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("Không đọc/lưu được ảnh minh chứng");
        }
    }

    @Transactional(readOnly = true)
    public List<LessonReportDto> list() {
        User actor = auth.current();
        return reports.findAll().stream().filter(r -> canRead(actor, r))
                .sorted(Comparator.comparing(LessonReport::getCreatedAt).reversed()).map(r -> dto(r, actor)).toList();
    }

    @Transactional(readOnly = true)
    public LessonReportDto detail(Long id) {
        LessonReport r = report(id);
        User actor = auth.current();
        AccessPolicy.require(actor.activeAccount() && actor.getId().equals(r.getReporter().getId()));
        return dto(r, actor);
    }

    public LessonReportDto review(Long id, Long version, LessonReport.Status status, String resolution) {
        LessonReport r = report(id);
        User actor = auth.current();
        AccessPolicy.require(AccessPolicy.allowsCourse(actor, "courses:update", r.getLesson().getCourse()));
        if (!Objects.equals(r.getVersion(), version))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Báo cáo đã thay đổi. Hãy tải lại.");
        if (status == null || status == LessonReport.Status.NEW)
            throw new BadRequestException("Chọn trạng thái xử lý hợp lệ");
        String note = resolution == null ? "" : resolution.trim();
        if (note.length() > 5000 || ((status == LessonReport.Status.RESOLVED || status == LessonReport.Status.DISMISSED)
                && note.isBlank()))
            throw new BadRequestException("Khi đóng báo cáo cần ghi kết quả xử lý (tối đa 5.000 ký tự)");
        boolean replyChanged = !note.isBlank() && !Objects.equals(note, r.getResolution());
        r.setStatus(status);
        r.setResolution(note);
        r.setReviewer(actor);
        r.setReviewedAt(LocalDateTime.now());
        reports.flush();
        if (replyChanged)
            notifications.create(r.getReporter().getUsername(), NotificationType.LESSON_REPORT,
                    "Phản hồi báo cáo nội dung bài học",
                    "Báo cáo của bạn về bài học " + r.getLesson().getTitle() + " đã có phản hồi.",
                    "/lesson-reports/" + r.getId());
        return dto(r, actor);
    }

    @Transactional(readOnly = true)
    public Path image(Long id, int index) {
        LessonReport r = report(id);
        User actor = auth.current();
        AccessPolicy.require(actor.getId().equals(r.getReporter().getId()) || canRead(actor, r));
        if (index < 0 || index >= r.getImages().size())
            throw new ResourceNotFoundException("Image");
        Path file = storage.resolve(r.getImages().get(index)).normalize();
        if (!file.startsWith(storage) || !Files.isRegularFile(file))
            throw new ResourceNotFoundException("Image");
        return file;
    }

    private LessonReportDto dto(LessonReport r, User actor) {
        return new LessonReportDto(r.getId(), r.getVersion(), r.getLesson().getId(), r.getLesson().getTitle(),
                r.getLesson().getCourse().getId(), r.getLesson().getCourse().getName(), r.getReporter().getFullName(),
                r.getContent(),
                java.util.stream.IntStream.range(0, r.getImages().size())
                        .mapToObj(i -> "/api/lesson-reports/" + r.getId() + "/images/" + i).toList(),
                r.getStatus(), r.getResolution(), r.getReviewer() == null ? null : r.getReviewer().getFullName(),
                r.getCreatedAt(), r.getReviewedAt(),
                AccessPolicy.allowsCourse(actor, "courses:update", r.getLesson().getCourse()));
    }
}
