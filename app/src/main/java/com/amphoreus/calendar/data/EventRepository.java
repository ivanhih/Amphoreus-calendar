package com.amphoreus.calendar.data;

import android.content.*;
import com.amphoreus.calendar.core.*;
import java.time.*;
import java.util.*;

public final class EventRepository {
    public final LocalStore local;
    public final SystemCalendar system;
    private final SharedPreferences preferences;
    public EventRepository(Context context) {
        local=new LocalStore(context); system=new SystemCalendar(context);
        preferences=context.getSharedPreferences("settings",Context.MODE_PRIVATE);
    }
    public List<CalendarSource> sources() { List<CalendarSource> out=new ArrayList<>(); out.add(CalendarSource.local()); out.addAll(system.sources()); return out; }
    public List<Occurrence> between(LocalDate from,LocalDate until) {
        ZoneId zone=ZoneId.systemDefault(); List<Occurrence> out=new ArrayList<>();
        Set<String> hidden=preferences.getStringSet("hiddenCalendars",Collections.emptySet());
        if(!hidden.contains("-1")) for(CalendarEvent e:local.all()) out.addAll(Recurrence.expand(e,from,until,zone));
        out.addAll(systemBetween(from,until,zone,hidden));
        out.sort(Comparator.comparingLong(o->o.start)); return out;
    }
    public List<Occurrence> systemBetween(LocalDate from,LocalDate until,ZoneId zone,Set<String> hidden) {
        List<Occurrence> out=new ArrayList<>(); Map<Long,LocalStore.AppReminder> appReminders=local.systemReminders();
        for(Occurrence occurrence:system.between(from,until,zone,hidden)) { LocalStore.AppReminder effective=applySystemReminder(occurrence.event,appReminders.get(occurrence.event.id)); if(effective!=null)appReminders.put(occurrence.event.id,effective); out.add(occurrence); }
        return out;
    }
    public CalendarEvent get(String key) {
        if(key==null || !key.matches("[ls]:[0-9]+")) throw new IllegalArgumentException("无效日程");
        long id=Long.parseLong(key.substring(2));
        if(key.startsWith("s:")) { CalendarEvent event=system.get(id); if(event!=null)applySystemReminder(event,local.systemReminder(id)); return event; }
        return local.get(id);
    }
    public long save(CalendarEvent e) throws Exception {
        if(!e.system) { e.appReminderMinutes=e.reminderMinutes; return local.save(e); }
        long id=system.save(e); e.id=id; local.saveSystemReminder(id,e.appReminderMinutes,e.reminderArt); return id;
    }
    public void saveSystemReminder(CalendarEvent e) { if(!e.system)throw new IllegalArgumentException("应用独立提醒只能附加到系统日程"); local.saveSystemReminder(e.id,e.appReminderMinutes,e.reminderArt); }
    public void delete(CalendarEvent e) { if(e.system) { system.delete(e.id); local.deleteSystemReminder(e.id); } else local.delete(e.id); }
    private LocalStore.AppReminder applySystemReminder(CalendarEvent event,LocalStore.AppReminder reminder) {
        if(reminder==null&&event.end>=System.currentTimeMillis()) {
            reminder=new LocalStore.AppReminder(0,CalendarEvent.RANDOM_ART); local.saveSystemReminder(event.id,reminder.minutes,reminder.art);
        }
        event.appReminderMinutes=reminder==null?-1:reminder.minutes; event.reminderArt=reminder==null?-1:reminder.art; return reminder;
    }
}
