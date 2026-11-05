package com.bidv.coretraining.flashcard.repository;

import com.bidv.coretraining.flashcard.entity.FlashcardStudyPlan;
import com.bidv.coretraining.flashcard.repository.projection.FlashcardDueReminderView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlashcardStudyPlanRepository extends JpaRepository<FlashcardStudyPlan, Long> {
    List<FlashcardStudyPlan> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<FlashcardStudyPlan> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Locks due plans until commit; SKIP LOCKED lets several backend instances share the work without double sends. */
    @Query(value = "select id from flashcard_study_plans where next_reminder_at <= :now "
            + "order by next_reminder_at limit :size for update skip locked", nativeQuery = true)
    List<Long> lockDueIds(@Param("now") Instant now, @Param("size") int size);

    /** Scalars only: loading User entities costs queries per recipient for their eager role collections. */
    @Query("select plan.id as id, plan.name as name, plan.startDate as startDate, plan.endDate as endDate, "
            + "plan.weekdayMask as weekdayMask, plan.reminderTime as reminderTime, plan.active as active, "
            + "plan.nextReminderAt as nextReminderAt, recipient.id as userId, recipient.username as username, "
            + "recipient.status as userStatus from FlashcardStudyPlan plan join plan.user recipient "
            + "where plan.id in :ids")
    List<FlashcardDueReminderView> findDueReminders(@Param("ids") Collection<Long> ids);
}
