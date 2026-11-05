package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.entity.Flashcard;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.enums.FlashcardOrigin;
import com.bidv.coretraining.enums.FlashcardType;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.impl.FlashcardLearningServiceImpl;
import com.bidv.coretraining.service.model.AuthorizedLesson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonalFlashcardTest {
    final FlashcardRepository flashcards = mock(FlashcardRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final LessonRepository lessons = mock(LessonRepository.class);
    final LearningAccessService access = mock(LearningAccessService.class);
    final FlashcardLearningService service = new FlashcardLearningServiceImpl(flashcards, users, lessons,
            mock(ChapterRepository.class), access);
    final User learner = User.builder().id(1L).username("learner").build();
    final Course course = Course.builder().id("C1").build();
    final Lesson lesson = Lesson.builder().id("L1").title("Bài 1").course(course).build();

    @BeforeEach void setup() {
        when(users.findByUsername("learner")).thenReturn(Optional.of(learner));
        when(access.requireLesson("learner", "L1")).thenReturn(new AuthorizedLesson(learner, course, null));
        when(access.requireLesson(learner, "L1")).thenReturn(new AuthorizedLesson(learner, course, null));
        when(lessons.findById("L1")).thenReturn(Optional.of(lesson));
        when(flashcards.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private Flashcard card(Long id, User owner) {
        LocalDateTime now = LocalDateTime.now();
        return Flashcard.builder().id(id).version(0L).course(course).lesson(lesson).owner(owner)
                .origin(owner == null ? FlashcardOrigin.MANUAL : FlashcardOrigin.PERSONAL).status("PUBLISHED")
                .front("CIF").back("Mã khách hàng").createdAt(now).updatedAt(now).build();
    }

    @Test void createdCardIsPublishedPersonalAndOwned() {
        var result = service.createPersonal("L1",
                new FlashcardDto.PersonalCardRequest(FlashcardType.TERM, "  GL ", " Sổ cái ", null), "learner");
        verify(flashcards).saveAndFlush(argThat(card -> card.getOwner() == learner
                && card.getOrigin() == FlashcardOrigin.PERSONAL && "PUBLISHED".equals(card.getStatus())
                && "GL".equals(card.getFront()) && card.getCourse() == course));
        assertThat(result.mine()).isTrue();
    }

    @Test void clozeRulesApplyToPersonalCards() {
        assertThatThrownBy(() -> service.createPersonal("L1",
                new FlashcardDto.PersonalCardRequest(FlashcardType.CLOZE, "Không có chỗ trống", "x", null),
                "learner")).isInstanceOf(BadRequestException.class);
        verify(flashcards, never()).saveAndFlush(any());
    }

    @Test void perLessonLimitIsEnforced() {
        when(flashcards.countByLessonIdAndOwnerId("L1", 1L)).thenReturn(200L);
        assertThatThrownBy(() -> service.createPersonal("L1",
                new FlashcardDto.PersonalCardRequest(FlashcardType.TERM, "a", "b", null), "learner"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void anotherLearnersCardReadsAsMissing() {
        when(flashcards.findByIdAndOwnerId(7L, 1L)).thenReturn(Optional.empty());
        var request = new FlashcardDto.PersonalCardRequest(FlashcardType.TERM, "a", "b", null);
        assertThatThrownBy(() -> service.updatePersonal(7L, request, "learner"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deletePersonal(7L, "learner"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(flashcards, never()).delete(any());
    }

    @Test void staleVersionIsRejected() {
        var mine = card(7L, learner);
        mine.setVersion(3L);
        when(flashcards.findByIdAndOwnerId(7L, 1L)).thenReturn(Optional.of(mine));
        assertThatThrownBy(() -> service.updatePersonal(7L,
                new FlashcardDto.PersonalCardRequest(FlashcardType.TERM, "a", "b", 2L), "learner"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void deleteRemovesTheLearnersCard() {
        var mine = card(7L, learner);
        when(flashcards.findByIdAndOwnerId(7L, 1L)).thenReturn(Optional.of(mine));
        service.deletePersonal(7L, "learner");
        verify(flashcards).delete(mine);
    }

    @Test void deckAppendsOwnCardsAfterLessonCards() {
        when(flashcards.findByLessonIdAndOwnerIsNullAndStatusOrderByCardOrderAscIdAsc("L1", "PUBLISHED"))
                .thenReturn(List.of(card(1L, null)));
        when(flashcards.findByLessonIdAndOwnerIdOrderByCreatedAtAscIdAsc("L1", 1L))
                .thenReturn(List.of(card(2L, learner)));
        var deck = service.lessonDeck("L1", "learner");
        assertThat(deck.cards()).extracting(FlashcardDto.Card::id).containsExactly(1L, 2L);
        assertThat(deck.cards()).extracting(FlashcardDto.Card::mine).containsExactly(false, true);
        assertThat(deck.cards().get(0).version()).isNull();
    }
}
