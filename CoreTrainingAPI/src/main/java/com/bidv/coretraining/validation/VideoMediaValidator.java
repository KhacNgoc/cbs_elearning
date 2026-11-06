package com.bidv.coretraining.validation;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class VideoMediaValidator {
    private static final int MAX_PROBE_OUTPUT = 65536;
    private final ObjectMapper mapper;
    private final String command;
    private final long timeoutSeconds;

    public VideoMediaValidator(ObjectMapper mapper,
            @Value("${app.storage.ffprobe-command:ffprobe}") String command,
            @Value("${app.storage.video-probe-timeout-seconds:30}") long timeoutSeconds) {
        this.mapper = mapper;
        this.command = command;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    /** Empty when the file cannot be probed, for example on a machine without ffprobe. */
    public java.util.OptionalDouble probeDuration(Path path) {
        Process process = null;
        try {
            process = new ProcessBuilder(command, "-v", "error", "-protocol_whitelist", "file",
                    "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", path.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS) || process.exitValue() != 0)
                return java.util.OptionalDouble.empty();
            double seconds = Double.parseDouble(new String(process.getInputStream().readNBytes(64)).trim());
            return Double.isFinite(seconds) && seconds > 0 ? java.util.OptionalDouble.of(seconds)
                    : java.util.OptionalDouble.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return java.util.OptionalDouble.empty();
        } catch (IOException | NumberFormatException exception) {
            return java.util.OptionalDouble.empty();
        } finally {
            if (process != null) process.destroyForcibly();
        }
    }

    public void validate(Path path, String extension) {
        Process process;
        try {
            process = new ProcessBuilder(command, "-v", "error", "-protocol_whitelist", "file",
                    "-show_entries", "format=format_name:stream=codec_type,codec_name,pix_fmt",
                    "-of", "json", path.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kiểm tra video. Máy chủ cần cài đặt ffprobe", exception);
        }
        ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "video-probe-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<byte[]> output = reader.submit(() -> {
            try (var stream = process.getInputStream()) {
                byte[] bytes = stream.readNBytes(MAX_PROBE_OUTPUT + 1);
                if (bytes.length > MAX_PROBE_OUTPUT) {
                    process.destroyForcibly();
                    throw new IOException("Video probe output exceeds limit");
                }
                return bytes;
            }
        });
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw invalid("Video quá phức tạp hoặc không hợp lệ; không thể kiểm tra trong thời gian cho phép");
            }
            if (process.exitValue() != 0) {
                throw invalid("File video bị hỏng hoặc không đúng định dạng");
            }
            validateMetadata(mapper.readTree(output.get(1, TimeUnit.SECONDS)), extension);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kiểm tra video bị gián đoạn", exception);
        } catch (IOException | ExecutionException | TimeoutException exception) {
            throw invalid("Không đọc được thông tin định dạng video");
        } finally {
            process.destroyForcibly();
            output.cancel(true);
            reader.shutdownNow();
        }
    }

    void validateMetadata(JsonNode metadata, String extension) {
        boolean webm = "webm".equals(extension);
        String container = metadata == null ? "" : metadata.path("format").path("format_name").asText();
        Set<String> containers = Set.of(container.split(","));
        if (webm ? !containers.contains("webm") : !containers.contains("mp4")) {
            throw invalid("Nội dung video không khớp với định dạng file");
        }
        int videos = 0;
        for (JsonNode stream : metadata.path("streams")) {
            String type = stream.path("codec_type").asText();
            String codec = stream.path("codec_name").asText();
            if ("video".equals(type)) {
                videos++;
                boolean supported = webm ? Set.of("vp8", "vp9").contains(codec) : "h264".equals(codec);
                // Limit to widely supported 8-bit 4:2:0 browser playback.
                if (!supported || !"yuv420p".equals(stream.path("pix_fmt").asText())) {
                    throw invalid("Codec video không hỗ trợ. Hãy dùng MP4 H.264 8-bit hoặc WebM VP8/VP9 8-bit");
                }
            } else if ("audio".equals(type)) {
                if (webm ? !Set.of("opus", "vorbis").contains(codec) : !"aac".equals(codec)) {
                    throw invalid("Codec âm thanh không hỗ trợ. Hãy dùng AAC cho MP4 hoặc Opus/Vorbis cho WebM");
                }
            } else {
                throw invalid("Video có luồng dữ liệu không hỗ trợ; hãy xuất lại chỉ với hình ảnh và âm thanh");
            }
        }
        if (videos != 1) {
            throw invalid("File phải chứa đúng một luồng video");
        }
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
