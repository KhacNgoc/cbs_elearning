package com.bidv.coretraining.video.service;

import com.bidv.coretraining.video.dto.VideoAccessDto;
import java.nio.file.Path;

/** Business operations for VideoAccessService. */
public interface VideoAccessService {
    public VideoAccessDto grantAccess(String lessonId, String username);

    public String authorizeStream(String token);

    public Path authorizeStreamPath(String token);
}
