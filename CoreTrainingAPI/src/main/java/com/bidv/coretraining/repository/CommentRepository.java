package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    org.springframework.data.domain.Page<Comment> findByLessonIdAndParentIsNull(String lessonId,
        org.springframework.data.domain.Pageable pageable);

    List<Comment> findByParentIdInOrderByCreatedAtAscIdAsc(java.util.Collection<Long> parentIds);

    List<Comment> findByLessonIdOrderByCreatedAtDesc(String lessonId);
}
