package com.bidv.coretraining.service;

import com.bidv.coretraining.enums.FlashcardRating;
import com.bidv.coretraining.policy.FlashcardSchedulingPolicy;
import com.bidv.coretraining.policy.FlashcardSchedulingPolicy.Schedule;
import com.bidv.coretraining.policy.FlashcardSchedulingPolicy.State;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FlashcardSchedulingPolicyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 9, 0);

    @Test void newCardForgottenReturnsWithinMinutesAndCountsALapse() {
        Schedule next = FlashcardSchedulingPolicy.next(null, FlashcardRating.AGAIN, NOW);
        assertThat(next.dueAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(next.state().repetitions()).isZero();
        assertThat(next.state().lapses()).isEqualTo(1);
        assertThat(next.state().easeFactor()).isCloseTo(2.3, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void rememberedCardsReturnAfterGrowingIntervals() {
        Schedule first = FlashcardSchedulingPolicy.next(null, FlashcardRating.GOOD, NOW);
        Schedule second = FlashcardSchedulingPolicy.next(first.state(), FlashcardRating.GOOD, NOW);
        Schedule third = FlashcardSchedulingPolicy.next(second.state(), FlashcardRating.GOOD, NOW);
        assertThat(first.state().intervalDays()).isEqualTo(1);
        assertThat(second.state().intervalDays()).isEqualTo(3);
        assertThat(third.state().intervalDays()).isEqualTo(8);
        assertThat(third.dueAt()).isEqualTo(NOW.plusDays(8));
    }

    @Test void betterRatingsNeverScheduleSooner() {
        for (State state : new State[] { State.initial(), new State(1, 1.3, 1, 0), new State(1, 1.3, 2, 2),
                new State(10, 2.5, 4, 0) }) {
            int hard = FlashcardSchedulingPolicy.next(state, FlashcardRating.HARD, NOW).state().intervalDays();
            int good = FlashcardSchedulingPolicy.next(state, FlashcardRating.GOOD, NOW).state().intervalDays();
            int easy = FlashcardSchedulingPolicy.next(state, FlashcardRating.EASY, NOW).state().intervalDays();
            assertThat(hard).isPositive().isLessThanOrEqualTo(good);
            assertThat(good).isLessThan(easy);
        }
    }

    @Test void easeStaysWithinBoundsAndIntervalIsCapped() {
        State lowEase = new State(5, 1.3, 3, 4);
        assertThat(FlashcardSchedulingPolicy.next(lowEase, FlashcardRating.HARD, NOW).state().easeFactor())
                .isEqualTo(1.3);
        State mature = new State(300, 2.8, 12, 0);
        assertThat(FlashcardSchedulingPolicy.next(mature, FlashcardRating.EASY, NOW).state().intervalDays())
                .isEqualTo(FlashcardSchedulingPolicy.MAX_INTERVAL_DAYS);
    }

    @Test void masteredFromThreeWeeks() {
        assertThat(FlashcardSchedulingPolicy.mastered(20)).isFalse();
        assertThat(FlashcardSchedulingPolicy.mastered(21)).isTrue();
        assertThat(FlashcardSchedulingPolicy.mastered(null)).isFalse();
    }
}
