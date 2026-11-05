package com.bidv.coretraining.course.service.impl;


import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.course.service.CourseImageService;
import com.bidv.coretraining.document.entity.UploadedAsset;
import com.bidv.coretraining.document.repository.UploadedAssetRepository;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.training.repository.TrainingProgramRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.io.*;
import java.util.UUID;

@Service
public class CourseImageServiceImpl implements CourseImageService {
    private final Path directory;
    private final AuthorizationService auth;
    private final CourseRepository courses;
    private final TrainingProgramRepository programs;
    private final UploadedAssetRepository assets;

    public CourseImageServiceImpl(@Value("${app.storage.documents-directory:uploads/documents}") String documents,
            AuthorizationService auth, CourseRepository courses, TrainingProgramRepository programs,
            UploadedAssetRepository assets) {
        this.directory = Path.of(documents).toAbsolutePath().normalize().resolve("course-images");
        this.auth = auth;
        this.courses = courses;
        this.programs = programs;
        this.assets = assets;
    }

    @Transactional
    public String upload(MultipartFile file, String courseId, String programId) {
        if (courseId != null && !courseId.isBlank())
            auth.edit(courseId);
        else
            auth.program(programs.findById(programId == null ? "" : programId).orElseThrow(
                    () -> new BadRequestException("Chọn chương trình trước khi tải ảnh")), "courses", "create");
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new BadRequestException("Ảnh phải nhỏ hơn hoặc bằng 5 MB");
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
                throw new BadRequestException("Chỉ hỗ trợ ảnh JPEG hoặc PNG");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase();
                if (!java.util.Set.of("jpeg", "jpg", "png").contains(format))
                    throw new BadRequestException("Chỉ hỗ trợ ảnh JPEG hoặc PNG");
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000L)
                    throw new BadRequestException("Ảnh vượt quá 20 triệu điểm ảnh");
                var image = reader.read(0); // Decode and re-encode; ignore supplied extension and metadata.
                String name = UUID.randomUUID() + ".png";
                Files.createDirectories(directory);
                ImageIO.write(image, "png", directory.resolve(name).toFile());
                String url = "/api/course-images/" + name;
                assets.save(new UploadedAsset(url, auth.current().getUsername()));
                return url;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("Không đọc/lưu được ảnh khóa học");
        }
    }

    @Transactional(readOnly = true)
    public void assertAttach(String path, String existing) {
        if (path == null || path.isBlank())
            return;
        if (path.equals(existing))
            return;
        if (!path.matches("/api/course-images/[a-f0-9-]{36}\\.png"))
            throw new BadRequestException("Hãy tải ảnh khóa học bằng chức năng tải ảnh");
        AccessPolicy.require(
                assets.findById(path).map(a -> a.getUploadedBy().equals(auth.current().getUsername())).orElse(false));
    }

    @Transactional(readOnly = true)
    public Path read(String name) {
        if (!name.matches("[a-f0-9-]{36}\\.png"))
            throw new BadRequestException("Tên ảnh không hợp lệ");
        String path = "/api/course-images/" + name;
        var user = auth.current();
        boolean owner = assets.findById(path).map(a -> a.getUploadedBy().equals(user.getUsername())).orElse(false);
        AccessPolicy.require(owner || courses.findAll().stream().anyMatch(c -> path.equals(c.getThumbnail()) &&
                (AccessPolicy.canEdit(user, c) || AccessPolicy.canReadCourse(user, c)
                        || "PUBLISHED".equals(c.getStatus()) && AccessPolicy.visible(user, c))));
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file))
            throw new com.bidv.coretraining.exception.ResourceNotFoundException("Ảnh khóa học");
        return file;
    }
}
