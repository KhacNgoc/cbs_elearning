package com.bidv.coretraining.document.service.impl;


import com.bidv.coretraining.document.dto.LessonDocumentDto;
import com.bidv.coretraining.document.service.DocumentStorageService;
import com.bidv.coretraining.enums.DocumentType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentStorageServiceImpl implements DocumentStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "ppt", "pptx");

    private final Path storageDirectory;

    public DocumentStorageServiceImpl(
            @Value("${app.storage.documents-directory:uploads/documents}") String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(storageDirectory);
    }

    public LessonDocumentDto store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File tài liệu không được để trống");
        }

        String originalName = StringUtils
                .cleanPath(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ file PDF, DOC, DOCX, PPT hoặc PPTX");
        }

        requireMatchingSignature(file, extension);

        String storedName = UUID.randomUUID() + "." + extension;
        Path destination = storageDirectory.resolve(storedName).normalize();
        if (!destination.startsWith(storageDirectory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ");
        }

        try {
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu file tài liệu",
                    exception);
        }

        return LessonDocumentDto.builder()
                .name(removeExtension(originalName))
                .documentType(resolveDocumentType(extension))
                .fileUrl("/uploads/documents/" + storedName)
                .originalFileName(originalName)
                .fileSize(file.getSize())
                .build();
    }

    private void requireMatchingSignature(MultipartFile file, String extension) {
        byte[] head;
        try (var input = file.getInputStream()) {
            head = input.readNBytes(8);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được file tài liệu", exception);
        }
        byte[] expected = switch (extension) {
            case "pdf" -> new byte[] { '%', 'P', 'D', 'F', '-' };
            case "docx", "pptx" -> new byte[] { 'P', 'K', 3, 4 };
            default -> new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };
        };
        if (head.length < expected.length || !java.util.Arrays.equals(head, 0, expected.length, expected, 0, expected.length)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nội dung file không đúng định dạng " + extension.toUpperCase(Locale.ROOT));
        }
    }

    public boolean exists(String fileUrl) {
        String prefix = "/uploads/documents/";
        if (fileUrl == null || !fileUrl.startsWith(prefix))
            return false;
        Path file = storageDirectory.resolve(fileUrl.substring(prefix.length())).normalize();
        return file.startsWith(storageDirectory) && Files.isRegularFile(file);
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? "" : fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String removeExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? fileName : fileName.substring(0, dotIndex);
    }

    private DocumentType resolveDocumentType(String extension) {
        return switch (extension) {
            case "pdf" -> DocumentType.PDF;
            case "ppt", "pptx" -> DocumentType.POWERPOINT;
            default -> DocumentType.WORD;
        };
    }
}
