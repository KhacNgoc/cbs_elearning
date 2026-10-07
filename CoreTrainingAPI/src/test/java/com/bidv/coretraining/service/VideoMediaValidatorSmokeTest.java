package com.bidv.coretraining.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional real ffprobe smoke, enabled with a directory holding valid.mp4 and invalid.mp4. */
class VideoMediaValidatorSmokeTest {
    private VideoMediaValidator validator;
    private Path directory;

    @BeforeEach
    void setup() {
        String fixtureDirectory = System.getenv("VIDEO_SMOKE_DIRECTORY");
        String probeCommand = System.getenv("VIDEO_SMOKE_FFPROBE_COMMAND");
        assumeTrue(fixtureDirectory != null && !fixtureDirectory.isBlank()
                && probeCommand != null && !probeCommand.isBlank(), "Real ffprobe smoke environment not configured");
        directory = Path.of(fixtureDirectory).toAbsolutePath();
        assertTrue(Files.isRegularFile(directory.resolve("valid.mp4")), "Missing valid.mp4 fixture");
        assertTrue(Files.isRegularFile(directory.resolve("invalid.mp4")), "Missing invalid.mp4 fixture");
        validator = new VideoMediaValidator(new ObjectMapper(), probeCommand, 30);
    }

    @Test
    void acceptsRealH264AacVideo() {
        assertDoesNotThrow(() -> validator.validate(directory.resolve("valid.mp4"), "mp4"));
    }

    @Test
    void rejectsRealUnsupportedOrInvalidVideo() {
        ResponseStatusException rejection = assertThrows(ResponseStatusException.class,
                () -> validator.validate(directory.resolve("invalid.mp4"), "mp4"));
        assertEquals(HttpStatus.BAD_REQUEST, rejection.getStatusCode());
    }
}
