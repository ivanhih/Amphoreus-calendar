package com.amphoreus.calendar.core;

import java.time.ZoneId;

/** All-day endpoints are UTC midnights; end is exclusive. Timed endpoints are instants. */
public final class CalendarEvent {
    /** Special artwork value: choose a different character image when the notification is built. */
    public static final int RANDOM_ART=-2;
    public long id;
    public boolean system;
    public long calendarId = -1;
    public String title = "", location = "", notes = "";
    public long start, end;
    public String zone = ZoneId.systemDefault().getId();
    public boolean allDay;
    public String recurrence = "";
    public int reminderMinutes = 10;
    /** The phone-calendar reminder for system events, or the app reminder for local events. */
    /** -1 means no independent app reminder for a system event. */
    public int appReminderMinutes = -1;
    /** -1 means no character image; 0-12 select the cover or one of the twelve monthly artworks. */
    public int reminderArt = -1;
    public int color = 0xffdec38d;
    public boolean writable = true, advanced;
    public String calendarName = "本地日历";
    public String key() { return (system ? "s:" : "l:") + id; }
    public CalendarEvent copy() {
        CalendarEvent e = new CalendarEvent();
        e.id=id; e.system=system; e.calendarId=calendarId; e.title=title; e.location=location;
        e.notes=notes; e.start=start; e.end=end; e.zone=zone; e.allDay=allDay;
        e.recurrence=recurrence; e.reminderMinutes=reminderMinutes; e.appReminderMinutes=appReminderMinutes; e.reminderArt=reminderArt; e.color=color;
        e.writable=writable; e.advanced=advanced; e.calendarName=calendarName;
        return e;
    }
    public void validate() {
        if (title.trim().isEmpty()) throw new IllegalArgumentException("请填写日程标题");
        if (end <= start) throw new IllegalArgumentException("结束时间必须晚于开始时间");
        if (allDay && (Math.floorMod(start, 86400000L) != 0 || Math.floorMod(end, 86400000L) != 0))
            throw new IllegalArgumentException("全天事件必须使用 UTC 日期边界");
        ZoneId.of(zone);
    }
}
