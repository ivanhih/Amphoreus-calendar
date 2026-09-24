package com.amphoreus.calendar.core;

/** Scheduling policy: a past reminder point does not make a future event stale. */
public final class ReminderSchedule {
    public static boolean stale(long occurrenceStart,long now) {
        return occurrenceStart<=now&&now-occurrenceStart>=86400000L;
    }
    private ReminderSchedule() {}
}
