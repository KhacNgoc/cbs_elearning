package com.bidv.coretraining.document.service.impl;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.learner.service.LearningAccessService;
import com.bidv.coretraining.document.entity.UploadedAsset;
import com.bidv.coretraining.document.repository.UploadedAssetRepository;
import com.bidv.coretraining.document.service.AssetAccessService;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssetAccessServiceImpl implements AssetAccessService {
    private final UploadedAssetRepository assets;
    private final LessonRepository lessons;
    private final AuthorizationService auth;
    private final LearningAccessService learningAccess;

    public AssetAccessServiceImpl(UploadedAssetRepository assets, LessonRepository lessons, AuthorizationService auth,
            LearningAccessService learningAccess) {
        this.assets = assets;
        this.lessons = lessons;
        this.auth = auth;
        this.learningAccess = learningAccess;
    }

    public void record(String path) {
        assets.save(new UploadedAsset(path, auth.current().getUsername()));
    }

    public void attach(String path) {
        if (path == null || !path.startsWith("/uploads/"))
            return;
        User user = auth.current();
        boolean owner = assets.findById(path).map(a -> a.getUploadedBy().equals(user.getUsername())).orElse(false);
        AccessPolicy.require(AccessPolicy.system(user) || owner
                || lessons.findByAssetPath(path).stream().anyMatch(l -> AccessPolicy.canEdit(user, l.getCourse())));
    }

    @Transactional(readOnly = true)
    public void read(String path) {
        User user = auth.current();
        if (AccessPolicy.system(user))
            return;
        if (assets.findById(path).map(a -> a.getUploadedBy().equals(user.getUsername())).orElse(false)
                && AccessPolicy.permissions(user).contains("CONTENT_EDIT"))
            return;
        // Runs on every file view, so a whole class opening one lesson repeats it at once: use the same
        // id/exists checks as opening the lesson instead of walking the course's chapters and lessons.
        boolean allowed = lessons.findIdsByAssetPath(path).stream().anyMatch(id -> {
            try {
                learningAccess.requireLesson(user, id);
                return true;
            } catch (org.springframework.security.access.AccessDeniedException e) {
                return false;
            }
        });
        AccessPolicy.require(allowed);
    }
}
