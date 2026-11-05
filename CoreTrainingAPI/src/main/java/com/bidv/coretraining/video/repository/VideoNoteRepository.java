package com.bidv.coretraining.video.repository;

import com.bidv.coretraining.video.entity.VideoNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoNoteRepository extends JpaRepository<VideoNote, Long> {
        List<VideoNote> findAllByLessonIdAndUserUsernameOrderByTimestampSecondsAscCreatedAtAsc(
                        String lessonId, String username);

        Optional<VideoNote> findByIdAndLessonIdAndUserUsername(
                        Long id, String lessonId, String username);
}
