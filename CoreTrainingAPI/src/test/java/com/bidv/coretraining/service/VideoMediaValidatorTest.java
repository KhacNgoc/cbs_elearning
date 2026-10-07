package com.bidv.coretraining.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoMediaValidatorTest {
    @TempDir Path directory;
    ObjectMapper mapper = new ObjectMapper();
    VideoMediaValidator validator = new VideoMediaValidator(mapper, "ffprobe", 2);
    void check(String format, String videoCodec, String pixelFormat, String audioCodec, String extension) throws Exception {
        validator.validateMetadata(mapper.readTree("{\"format\":{\"format_name\":\"" + format + "\"},\"streams\":[{\"codec_type\":\"video\",\"codec_name\":\"" + videoCodec + "\",\"pix_fmt\":\"" + pixelFormat + "\"},{\"codec_type\":\"audio\",\"codec_name\":\"" + audioCodec + "\"}]}"), extension);
    }
    VideoMediaValidator script(String body, long timeout) throws Exception {
        Path script = Files.writeString(directory.resolve("probe"), "#!/bin/sh\n" + body + "\n");
        assertTrue(script.toFile().setExecutable(true));
        return new VideoMediaValidator(mapper, script.toString(), timeout);
    }
    @Test void invokesProbeAndAcceptsItsJsonOutput() throws Exception {
        script("printf '%s' '{\"format\":{\"format_name\":\"mp4\"},\"streams\":[{\"codec_type\":\"video\",\"codec_name\":\"h264\",\"pix_fmt\":\"yuv420p\"}]}'", 2).validate(directory.resolve("file.mp4"), "mp4");
    }
    @Test void stopsTimedOutProbe() throws Exception {
        VideoMediaValidator slow = script("exec sleep 10", 1);
        long start = System.nanoTime();
        assertThrows(ResponseStatusException.class, () -> slow.validate(directory.resolve("file.mp4"), "mp4"));
        assertTrue(java.time.Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5);
    }
    @Test void rejectsOversizedProbeOutput() throws Exception {
        VideoMediaValidator oversized = script("exec head -c 70000 /dev/zero", 2);
        assertThrows(ResponseStatusException.class, () -> oversized.validate(directory.resolve("file.mp4"), "mp4"));
    }
    @Test void unavailableProbeGivesServiceUnavailable() {
        VideoMediaValidator missing = new VideoMediaValidator(mapper, directory.resolve("missing").toString(), 1);
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class, () -> missing.validate(directory.resolve("file.mp4"), "mp4")).getStatusCode());
    }
    @Test void acceptsH264AacAndWebmBrowserCodecs() throws Exception { check("mov,mp4,m4a,3gp,3g2,mj2", "h264", "yuv420p", "aac", "mp4"); check("matroska,webm", "vp9", "yuv420p", "opus", "webm"); }
    @Test void rejectsHevcAnd10BitAndUnsupportedAudio() { assertThrows(ResponseStatusException.class, () -> check("mp4", "hevc", "yuv420p", "aac", "mp4")); assertThrows(ResponseStatusException.class, () -> check("mp4", "h264", "yuv420p10le", "aac", "mp4")); assertThrows(ResponseStatusException.class, () -> check("mp4", "h264", "yuv420p", "pcm_s16le", "mp4")); }
    @Test void rejectsRenamedContainerAndAudioOnlyFile() throws Exception { assertThrows(ResponseStatusException.class, () -> check("matroska,webm", "h264", "yuv420p", "aac", "mp4")); assertThrows(ResponseStatusException.class, () -> validator.validateMetadata(mapper.readTree("{\"format\":{\"format_name\":\"mp4\"},\"streams\":[]}"), "mp4")); }
    @Test void removesRejectedStoredFile() throws Exception {
        VideoMediaValidator mockValidator = mock(VideoMediaValidator.class);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid")).when(mockValidator).validate(any(), eq("mp4"));
        VideoStorageService storage = new VideoStorageService(directory.toString(), mockValidator);
        storage.initialize();
        assertThrows(ResponseStatusException.class, () -> storage.store(new MockMultipartFile("file", "test.mp4", "video/mp4", new byte[]{1,2,3})));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
    @Test void storesValidatedFileAndRejectsUnsupportedExtension() throws Exception {
        VideoMediaValidator mockValidator = mock(VideoMediaValidator.class);
        VideoStorageService storage = new VideoStorageService(directory.toString(), mockValidator);
        storage.initialize();
        String url = storage.store(new MockMultipartFile("file", "test.mp4", "video/mp4", new byte[]{1,2,3}));
        assertTrue(Files.isRegularFile(directory.resolve(url.substring("/uploads/videos/".length()))));
        verify(mockValidator).validate(any(), eq("mp4"));
        assertThrows(ResponseStatusException.class, () -> storage.store(new MockMultipartFile("file", "test.mov", "video/quicktime", new byte[]{1})));
    }
}
