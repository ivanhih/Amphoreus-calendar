package com.amphoreus.calendar.core;

import java.time.*;

public final class ReminderTime {
    public static long trigger(Occurrence o,ZoneId zone) {
        return trigger(o,zone,o.event.reminderMinutes);
    }
    public static long trigger(Occurrence o,ZoneId zone,int reminderMinutes) {
        long base=o.event.allDay?o.firstDate(zone).atTime(9,0).atZone(zone).toInstant().toEpochMilli():o.start;
        return base-reminderMinutes*60000L;
    }
    private ReminderTime() {}
}
