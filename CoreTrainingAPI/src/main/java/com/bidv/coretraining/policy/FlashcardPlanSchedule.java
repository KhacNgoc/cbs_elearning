package com.bidv.coretraining.policy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** When a study plan reminds its learner. Learners pick Vietnam wall-clock times, whatever zone the server runs in. */
public final class FlashcardPlanSchedule {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int EVERY_DAY = 0b1111111;

    private FlashcardPlanSchedule() {
    }

    /** The first reminder strictly after the given moment, or null when none remains. */
    public static Instant nextReminder(LocalDate start, LocalDate end, int weekdayMask, LocalTime time, Instant after) {
        if (time == null)
            return null;
        LocalDate today = after.atZone(ZONE).toLocalDate();
        for (LocalDate date = start.isAfter(today) ? start : today; !date.isAfter(end); date = date.plusDays(1)) {
            if ((weekdayMask & bit(date.getDayOfWeek())) == 0)
                continue;
            Instant reminder = date.atTime(time).atZone(ZONE).toInstant();
            if (reminder.isAfter(after))
                return reminder;
        }
        return null;
    }

    /** ISO weekdays, 1 = Monday through 7 = Sunday. */
    public static int mask(Collection<Integer> weekdays) {
        int mask = 0;
        for (int weekday : weekdays)
            mask |= bit(DayOfWeek.of(weekday));
        return mask;
    }

    public static List<Integer> weekdays(int mask) {
        List<Integer> result = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values())
            if ((mask & bit(day)) != 0)
                result.add(day.getValue());
        return result;
    }

    private static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }
}
