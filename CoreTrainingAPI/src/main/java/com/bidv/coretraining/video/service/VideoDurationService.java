package com.bidv.coretraining.video.service;

import java.util.OptionalDouble;

public interface VideoDurationService {
    /** Empty for external videos and for files that cannot be read. */
    OptionalDouble durationOf(String videoUrl);
}
