package com.bidv.coretraining.document.controller;

import com.bidv.coretraining.document.service.AssetAccessService;
import com.bidv.coretraining.document.service.DocumentStorageService;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.*;
import java.nio.file.*;

@RestController
public class ProtectedDocumentController {
    private final DocumentStorageService storage;
    private final AssetAccessService access;

    public ProtectedDocumentController(DocumentStorageService storage, AssetAccessService access) {
        this.storage = storage;
        this.access = access;
    }

    @GetMapping("/uploads/documents/{name}")
    public ResponseEntity<Resource> read(@PathVariable String name) throws java.io.IOException {
        if (name.contains("..") || name.contains("/") || name.contains("\\"))
            throw new ResourceNotFoundException("Document");
        access.read("/uploads/documents/" + name);
        Path path = storage.getStorageDirectory().resolve(name).normalize();
        if (!path.startsWith(storage.getStorageDirectory()) || !Files.isRegularFile(path))
            throw new ResourceNotFoundException("Document");
        MediaType type = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type)
                .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(path));
    }
}
