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

    @org.springframework.data.jpa.repository.Query(value = """
            select ranked.* from (
              select c.*, row_number() over (partition by c.parent_comment_id order by c.created_at, c.id) as reply_rank
              from comments c where c.lesson_id = :lessonId and c.parent_comment_id in (:parentIds)
            ) ranked where ranked.reply_rank <= :previewSize
            order by ranked.parent_comment_id, ranked.reply_rank
            """, nativeQuery = true)
    List<Comment> findReplyPreviews(@org.springframework.data.repository.query.Param("lessonId") String lessonId,
            @org.springframework.data.repository.query.Param("parentIds") java.util.Collection<Long> parentIds,
            @org.springframework.data.repository.query.Param("previewSize") int previewSize);

    List<Comment> findByLessonIdAndParentIdOrderByCreatedAtAscIdAsc(String lessonId, Long parentId,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select c from Comment c where c.lesson.id = :lessonId and c.parent.id = :parentId
              and (c.createdAt > :afterTime or (c.createdAt = :afterTime and c.id > :afterId))
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findRepliesAfter(@org.springframework.data.repository.query.Param("lessonId") String lessonId,
            @org.springframework.data.repository.query.Param("parentId") Long parentId,
            @org.springframework.data.repository.query.Param("afterTime") java.time.LocalDateTime afterTime,
            @org.springframework.data.repository.query.Param("afterId") Long afterId,
            org.springframework.data.domain.Pageable pageable);

    List<Comment> findByLessonIdOrderByCreatedAtDesc(String lessonId);
}
