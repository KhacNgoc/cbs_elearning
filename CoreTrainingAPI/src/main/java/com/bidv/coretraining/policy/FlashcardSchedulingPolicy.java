package com.bidv.coretraining.policy;

import com.bidv.coretraining.enums.FlashcardRating;

import java.time.LocalDateTime;

/**
 * SM-2 style spaced repetition. A forgotten card returns in ten minutes; remembered
 * cards return after a growing number of days. Better ratings never schedule a card
 * sooner than worse ones.
 */
public final class FlashcardSchedulingPolicy {
    public static final double INITIAL_EASE = 2.5;
    public static final double MIN_EASE = 1.3;
    public static final int MAX_INTERVAL_DAYS = 365;
    /** Interval from which a card counts as memorised. */
    public static final int MASTERED_INTERVAL_DAYS = 21;
    private static final int RELEARN_MINUTES = 10;

    private FlashcardSchedulingPolicy() {
    }

    public record State(int intervalDays, double easeFactor, int repetitions, int lapses) {
        public static State initial() {
            return new State(0, INITIAL_EASE, 0, 0);
        }
    }

    public record Schedule(State state, LocalDateTime dueAt) {
    }

    public static Schedule next(State current, FlashcardRating rating, LocalDateTime now) {
        State state = current == null ? State.initial() : current;
        if (rating == FlashcardRating.AGAIN) {
            State lapsed = new State(0, Math.max(MIN_EASE, state.easeFactor() - 0.2), 0, state.lapses() + 1);
            return new Schedule(lapsed, now.plusMinutes(RELEARN_MINUTES));
        }
        int interval = state.intervalDays();
        int hard = state.repetitions() == 0 ? 1 : Math.max(interval + 1, (int) Math.round(interval * 1.2));
        int good = state.repetitions() == 0 ? 1
                : state.repetitions() == 1 ? 3 : (int) Math.round(interval * state.easeFactor());
        good = Math.max(hard + (state.repetitions() == 0 ? 0 : 1), good);
        int easy = Math.max(good + 1, state.repetitions() == 0 ? 3 : (int) Math.round(good * 1.3));

        double ease = state.easeFactor();
        int days;
        switch (rating) {
            case HARD -> {
                ease = Math.max(MIN_EASE, ease - 0.15);
                days = hard;
            }
            case EASY -> {
                ease = ease + 0.15;
                days = easy;
            }
            default -> days = good;
        }
        days = Math.min(MAX_INTERVAL_DAYS, days);
        State updated = new State(days, ease, state.repetitions() + 1, state.lapses());
        return new Schedule(updated, now.plusDays(days));
    }

    public static boolean mastered(Integer intervalDays) {
        return intervalDays != null && intervalDays >= MASTERED_INTERVAL_DAYS;
    }
}
