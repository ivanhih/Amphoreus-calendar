package com.amphoreus.calendar.core;

import java.time.*;

/**
 * Converts the date/time shown by the editor without losing the user's wall-clock value.
 * Android's picker reports an hour-of-day; this class keeps that value in the event zone
 * and is deliberately independent of the device default timezone.
 */
public final class EventTime {
    public static long toMillis(LocalDate date,LocalTime time,ZoneId zone) {
        if(date==null||time==null||zone==null) throw new IllegalArgumentException("event time is incomplete");
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli();
    }
    public static LocalDate date(long millis,ZoneId zone) {
        if(zone==null) throw new IllegalArgumentException("event zone is missing");
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
    }
    public static LocalTime time(long millis,ZoneId zone) {
        if(zone==null) throw new IllegalArgumentException("event zone is missing");
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalTime();
    }
    public static LocalDateTime endAfterHour(LocalDate date,LocalTime start) {
        if(date==null||start==null) throw new IllegalArgumentException("event start is incomplete");
        return LocalDateTime.of(date,start).plusHours(1);
    }
    private EventTime() {}
}
