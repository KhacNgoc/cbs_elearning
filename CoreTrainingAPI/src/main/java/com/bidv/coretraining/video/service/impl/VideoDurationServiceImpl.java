package com.bidv.coretraining.video.service.impl;

import com.bidv.coretraining.validation.VideoMediaValidator;
import com.bidv.coretraining.video.service.VideoDurationService;
import com.bidv.coretraining.video.service.VideoStorageService;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Duration of an uploaded video as read from the file. Stored names are unique, so a result never goes stale. */
@Service
public class VideoDurationServiceImpl implements VideoDurationService {
    private static final String PREFIX = "/uploads/videos/";
    private final VideoStorageService storage;
    private final VideoMediaValidator probe;
    private final Map<String, Double> known = new ConcurrentHashMap<>();

    public VideoDurationServiceImpl(VideoStorageService storage, VideoMediaValidator probe) {
        this.storage = storage;
        this.probe = probe;
    }

    @Override
    public OptionalDouble durationOf(String videoUrl) {
        if (videoUrl == null || !videoUrl.startsWith(PREFIX))
            return OptionalDouble.empty();
        Double cached = known.get(videoUrl);
        if (cached != null)
            return OptionalDouble.of(cached);
        Path directory = storage.getStorageDirectory();
        Path file = directory.resolve(videoUrl.substring(PREFIX.length())).normalize();
        if (!file.startsWith(directory))
            return OptionalDouble.empty();
        OptionalDouble duration = probe.probeDuration(file);
        duration.ifPresent(seconds -> known.put(videoUrl, seconds));
        return duration;
    }
}
