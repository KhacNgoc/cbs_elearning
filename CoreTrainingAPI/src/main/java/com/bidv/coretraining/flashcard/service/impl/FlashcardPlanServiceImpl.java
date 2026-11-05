package com.bidv.coretraining.flashcard.service.impl;

import com.bidv.coretraining.enums.NotificationType;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.flashcard.dto.FlashcardDto;
import com.bidv.coretraining.flashcard.dto.FlashcardPlanDto;
import com.bidv.coretraining.flashcard.entity.FlashcardStudyPlan;
import com.bidv.coretraining.flashcard.repository.FlashcardPlanNativeRepository;
import com.bidv.coretraining.flashcard.repository.FlashcardStudyPlanRepository;
import com.bidv.coretraining.flashcard.repository.projection.FlashcardDueReminderView;
import com.bidv.coretraining.flashcard.service.FlashcardLearningService;
import com.bidv.coretraining.flashcard.service.FlashcardPlanService;
import com.bidv.coretraining.notification.dto.NotificationDraft;
import com.bidv.coretraining.notification.service.NotificationService;
import com.bidv.coretraining.policy.FlashcardPlanSchedule;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlashcardPlanServiceImpl implements FlashcardPlanService {
    static final int MAX_PLANS_PER_USER = 20;
    private static final int MAX_DAYS = 366;
    /** A reminder this late is noise, for example at dawn after an outage overnight. */
    private static final Duration MAX_REMINDER_DELAY = Duration.ofHours(2);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FlashcardStudyPlanRepository plans;
    private final FlashcardPlanNativeRepository planQueries;
    private final UserRepository users;
    private final FlashcardLearningService flashcards;
    private final NotificationService notifications;

    @Override
    @Transactional(readOnly = true)
    public List<FlashcardPlanDto.Plan> list(String username) {
        return plans.findByUserIdOrderByCreatedAtDescIdDesc(user(username).getId()).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public FlashcardPlanDto.Plan create(FlashcardPlanDto.Request request, String username) {
        User user = user(username);
        if (plans.countByUserId(user.getId()) >= MAX_PLANS_PER_USER)
            throw new BadRequestException("Mỗi học viên chỉ tạo được tối đa " + MAX_PLANS_PER_USER + " lịch học");
        if (request.endDate().isBefore(LocalDate.now(FlashcardPlanSchedule.ZONE)))
            throw new BadRequestException("Ngày kết thúc đã qua");
        if (request.items() == null)
            throw new BadRequestException("Chọn ít nhất một thẻ cho lịch học");
        LocalDateTime now = LocalDateTime.now();
        FlashcardStudyPlan plan = FlashcardStudyPlan.builder().user(user).createdAt(now).build();
        apply(plan, request, username);
        return toDto(plans.saveAndFlush(plan));
    }

    @Override
    @Transactional
    public FlashcardPlanDto.Plan update(Long id, FlashcardPlanDto.Request request, String username) {
        FlashcardStudyPlan plan = owned(id, username);
        if (request.version() != null && !Objects.equals(plan.getVersion(), request.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lịch học đã được sửa ở nơi khác. Hãy tải lại");
        apply(plan, request, username);
        // Flush so the returned version matches the row for the next optimistic update.
        return toDto(plans.saveAndFlush(plan));
    }

    @Override
    @Transactional
    public void delete(Long id, String username) {
        plans.delete(owned(id, username));
    }

    @Override
    @Transactional(readOnly = true)
    public FlashcardPlanDto.Session session(Long id, String username) {
        FlashcardStudyPlan plan = owned(id, username);
        Map<String, Set<Long>> chosen = new HashMap<>();
        for (FlashcardPlanDto.Item item : items(plan))
            chosen.put(item.lessonId(), item.cardIds() == null ? null : new HashSet<>(item.cardIds()));
        List<String> open = flashcards.openLessonIds(chosen.keySet(), username);
        List<FlashcardDto.Card> cards = flashcards.cardsInLessons(open, username).stream().filter(card -> {
            Set<Long> ids = chosen.get(card.lessonId());
            return ids == null || ids.contains(card.id());
        }).toList();
        return new FlashcardPlanDto.Session(plan.getId(), plan.getName(), cards);
    }

    @Override
    @Transactional
    public int sendDueReminders(Instant now, int batchSize) {
        List<Long> ids = plans.lockDueIds(now, batchSize);
        if (ids.isEmpty())
            return 0;
        List<NotificationDraft> drafts = new ArrayList<>();
        Map<Long, Instant> nextReminders = new HashMap<>();
        for (FlashcardDueReminderView plan : plans.findDueReminders(ids)) {
            boolean onTime = Duration.between(plan.getNextReminderAt(), now).compareTo(MAX_REMINDER_DELAY) <= 0;
            if (onTime && User.activeStatus(plan.getUserStatus())) {
                drafts.add(new NotificationDraft(plan.getUserId(), plan.getUsername(),
                        NotificationType.FLASHCARD_STUDY_REMINDER, "Đến giờ học flashcard",
                        "Lịch \"" + plan.getName() + "\" đã đến giờ học.", "/flashcards?plan=" + plan.getId()));
            }
            nextReminders.put(plan.getId(), !plan.getActive() ? null : FlashcardPlanSchedule.nextReminder(
                    plan.getStartDate(), plan.getEndDate(), plan.getWeekdayMask(), plan.getReminderTime(), now));
        }
        notifications.createAll(drafts);
        planQueries.updateNextReminders(nextReminders);
        return ids.size();
    }

    private void apply(FlashcardStudyPlan plan, FlashcardPlanDto.Request request, String username) {
        if (request.endDate().isBefore(request.startDate()))
            throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu");
        if (request.startDate().plusDays(MAX_DAYS).isBefore(request.endDate()))
            throw new BadRequestException("Lịch học kéo dài tối đa " + MAX_DAYS + " ngày");
        boolean singleDay = request.startDate().equals(request.endDate());
        List<Integer> weekdays = request.weekdays() == null ? List.of() : request.weekdays();
        if (!singleDay && weekdays.isEmpty())
            throw new BadRequestException("Chọn ít nhất một ngày trong tuần");
        if (request.items() != null)
            plan.setSelection(selection(request.items(), username));
        plan.setName(request.name().trim());
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        plan.setWeekdayMask(singleDay ? FlashcardPlanSchedule.EVERY_DAY : FlashcardPlanSchedule.mask(weekdays));
        plan.setReminderTime(request.reminderTime());
        plan.setActive(request.active() == null || request.active());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setNextReminderAt(next(plan, Instant.now()));
    }

    private static Instant next(FlashcardStudyPlan plan, Instant after) {
        return !plan.isActive() ? null : FlashcardPlanSchedule.nextReminder(plan.getStartDate(), plan.getEndDate(),
                plan.getWeekdayMask(), plan.getReminderTime(), after);
    }

    private String selection(List<FlashcardPlanDto.Item> items, String username) {
        if (items.isEmpty())
            throw new BadRequestException("Chọn ít nhất một thẻ cho lịch học");
        Set<String> open = new HashSet<>(
                flashcards.openLessonIds(items.stream().map(FlashcardPlanDto.Item::lessonId).toList(), username));
        for (FlashcardPlanDto.Item item : items) {
            if (!open.contains(item.lessonId()))
                throw new BadRequestException("Có bài học bạn chưa được mở khóa");
            if (item.cardIds() != null && item.cardIds().isEmpty())
                throw new BadRequestException("Chọn ít nhất một thẻ cho lịch học");
        }
        try {
            return JSON.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store a study plan's selection", e);
        }
    }

    private static List<FlashcardPlanDto.Item> items(FlashcardStudyPlan plan) {
        try {
            return JSON.readValue(plan.getSelection(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored study plan " + plan.getId() + " is unreadable", e);
        }
    }

    private FlashcardPlanDto.Plan toDto(FlashcardStudyPlan plan) {
        return new FlashcardPlanDto.Plan(plan.getId(), plan.getName(), plan.getStartDate(), plan.getEndDate(),
                FlashcardPlanSchedule.weekdays(plan.getWeekdayMask()), plan.getReminderTime(), plan.isActive(),
                plan.getNextReminderAt(), items(plan), plan.getVersion());
    }

    /** Another learner's plan reads as missing so its existence is not revealed. */
    private FlashcardStudyPlan owned(Long id, String username) {
        return plans.findByIdAndUserId(id, user(username).getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lịch học", "id", id));
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }
}
