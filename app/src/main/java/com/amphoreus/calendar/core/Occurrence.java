package com.amphoreus.calendar.core;

import java.time.*;

public final class Occurrence {
    public final CalendarEvent event;
    public final long start, end;
    public Occurrence(CalendarEvent event, long start, long end) { this.event=event; this.start=start; this.end=end; }
    public LocalDate firstDate(ZoneId zone) { return Instant.ofEpochMilli(start).atZone(event.allDay ? ZoneOffset.UTC : zone).toLocalDate(); }
    public LocalDate lastDate(ZoneId zone) { return Instant.ofEpochMilli(end-1).atZone(event.allDay ? ZoneOffset.UTC : zone).toLocalDate(); }
    public boolean onDate(LocalDate date, ZoneId zone) { return !date.isBefore(firstDate(zone)) && !date.isAfter(lastDate(zone)); }
    public boolean overlaps(LocalDate from, LocalDate until, ZoneId zone) { return firstDate(zone).isBefore(until) && !lastDate(zone).isBefore(from); }
}
