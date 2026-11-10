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
    /**
     * Browsers may keep a copy but must revalidate every use, so each view still passes the access
     * check (a revoked learner gets 403) while an unchanged file costs a 304 instead of a full download.
     */
    static final CacheControl CACHE = CacheControl.noCache().cachePrivate();

    private final DocumentStorageService storage;
    private final AssetAccessService access;

    public ProtectedDocumentController(DocumentStorageService storage, AssetAccessService access) {
        this.storage = storage;
        this.access = access;
    }

    @GetMapping("/uploads/documents/{name}")
    public ResponseEntity<?> read(@PathVariable String name,
            @RequestHeader(value = "X-Accel-Supported", required = false) String accelSupported)
            throws java.io.IOException {
        if (name.contains("..") || name.contains("/") || name.contains("\\"))
            throw new ResourceNotFoundException("Document");
        access.read("/uploads/documents/" + name);
        Path path = storage.getStorageDirectory().resolve(name).normalize();
        if (!path.startsWith(storage.getStorageDirectory()) || !Files.isRegularFile(path))
            throw new ResourceNotFoundException("Document");
        // Behind nginx the file, its ETag/304 and byte ranges are served by nginx itself, so this
        // request returns as soon as access is checked instead of holding a Tomcat thread per download.
        // nginx's internal location sets Cache-Control; sending it here too would duplicate the header.
        if ("true".equalsIgnoreCase(accelSupported))
            return ResponseEntity.ok().header("X-Accel-Redirect", "/_protected_documents/" + name).build();
        MediaType type = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
        long size = Files.size(path), modified = Files.getLastModifiedTime(path).toMillis();
        // Stored names are random UUIDs that are never overwritten, so size and mtime identify the content.
        return ResponseEntity.ok().cacheControl(CACHE).eTag(Long.toHexString(modified) + "-" + Long.toHexString(size))
                .lastModified(modified).contentType(type)
                .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(path));
    }
}
