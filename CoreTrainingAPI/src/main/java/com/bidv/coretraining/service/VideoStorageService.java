package com.bidv.coretraining.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class VideoStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "webm", "m4v");
    private final Path storageDirectory;
    private final VideoMediaValidator mediaValidator;

    public VideoStorageService(@Value("${app.storage.videos-directory:uploads/videos}") String storageDirectory,
                               VideoMediaValidator mediaValidator) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
        this.mediaValidator = mediaValidator;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(storageDirectory);
    }

    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File video không được để trống");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "video" : file.getOriginalFilename());
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ MP4/M4V (H.264/AAC) hoặc WebM (VP8/VP9, Opus/Vorbis)");
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path destination = storageDirectory.resolve(storedName).normalize();
        if (!destination.startsWith(storageDirectory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ");
        }

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            mediaValidator.validate(destination, extension);
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            if (exception instanceof ResponseStatusException responseException) {
                throw responseException;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu file video", exception);
        }
        return "/uploads/videos/" + storedName;
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? "" : fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
