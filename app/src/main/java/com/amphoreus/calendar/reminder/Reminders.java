package com.amphoreus.calendar.reminder;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import com.amphoreus.calendar.CalendarApp;
import com.amphoreus.calendar.core.*;
import com.amphoreus.calendar.data.EventRepository;
import java.time.*;
import java.util.*;

/** Alarm scheduling for both local events and app-owned reminders attached to system events. */
public final class Reminders {
    public static final String CHANNEL="local_calendar_reminders";
    public static void createChannel(Context c) { c.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"本地日程提醒",NotificationManager.IMPORTANCE_HIGH)); }
    public static boolean exactAllowed(Context c) { return Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms(); }

    private static final class Candidate {
        final String key; final CalendarEvent event; final Occurrence occurrence; final int minutes;
        Candidate(String key,CalendarEvent event,Occurrence occurrence,int minutes) { this.key=key; this.event=event; this.occurrence=occurrence; this.minutes=minutes; }
    }
    private static Intent alarmIntent(Context c,String key) {
        String path=key.replace(':','/');
        return new Intent(c,ReminderReceiver.class).setData(Uri.parse("amphoreus://reminder/"+path)).putExtra("eventKey",key);
    }
    private static String normalizeKey(String key) { return key.startsWith("l:")||key.startsWith("s:")||key.startsWith("i:")?key:"l:"+key; }
    private static String prefKey(String key) { return key.replace(':','_'); }
    private static long deliveredAt(SharedPreferences delivered,String key) {
        long value=delivered.getLong("last_"+prefKey(key),Long.MIN_VALUE);
        if(value==Long.MIN_VALUE&&key.startsWith("l:"))value=delivered.getLong("last_"+key.substring(2),Long.MIN_VALUE);
        return value;
    }
    public static int appReminderMinutes(CalendarEvent event) { return event.system?event.appReminderMinutes:event.reminderMinutes; }
    public static String fingerprint(CalendarEvent e) {
        return e.start+"/"+e.end+"/"+e.allDay+"/"+e.zone+"/"+e.recurrence+"/"+appReminderMinutes(e)+"/"+e.reminderArt+"/"+e.title+"/"+e.system+"/"+e.imported+"/"+e.importUid;
    }
    public static void reschedule(Context c) {
        CalendarApp app=(CalendarApp)c.getApplicationContext(); EventRepository repository=app.repository();
        AlarmManager manager=c.getSystemService(AlarmManager.class); SharedPreferences p=c.getSharedPreferences("reminders",0);
        Set<String> rawPrevious=p.getStringSet("ids",Collections.emptySet());
        for(String raw:rawPrevious) {
            String key=normalizeKey(raw);
            PendingIntent existing=PendingIntent.getBroadcast(c,0,alarmIntent(c,key),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
            if(existing!=null)manager.cancel(existing);
        }
        Set<String> scheduled=new HashSet<>(); SharedPreferences.Editor editor=p.edit();
        for(String raw:rawPrevious) {
            String key=normalizeKey(raw); editor.remove("start_"+raw).remove("trigger_"+raw).remove("fingerprint_"+raw);
            if(!key.equals(raw))editor.remove("start_"+key).remove("trigger_"+key).remove("fingerprint_"+key);
        }
        SharedPreferences delivered=c.getSharedPreferences("delivered_reminders",0);
        ZoneId zone=ZoneId.systemDefault(); LocalDate today=LocalDate.now(); long now=System.currentTimeMillis();
        List<Candidate> candidates=new ArrayList<>();
        for(CalendarEvent e:repository.local.all()) {
            if(e.reminderMinutes<0)continue;
            for(Occurrence occurrence:Recurrence.expand(e,today.minusDays(2),today.plusYears(8).plusDays(2),zone))
                candidates.add(new Candidate("l:"+e.id,e,occurrence,e.reminderMinutes));
        }
        if(repository.system.canRead()) {
            try {
                for(Occurrence occurrence:repository.systemBetween(today.minusDays(2),today.plusYears(8).plusDays(2),zone,Collections.emptySet())) {
                    CalendarEvent e=occurrence.event;
                    if(e.appReminderMinutes>=0)candidates.add(new Candidate("s:"+e.id,e,occurrence,e.appReminderMinutes));
                }
            } catch(SecurityException ignored) { /* Calendar permission may have been revoked between the check and the query. */ }
        }
        try {
            for(Occurrence occurrence:repository.importedBetween(today.minusDays(2),today.plusYears(8).plusDays(2),zone)) {
                CalendarEvent e=occurrence.event; if(e.appReminderMinutes>=0)candidates.add(new Candidate("i:"+e.id,e,occurrence,e.appReminderMinutes));
            }
        } catch(RuntimeException ignored) { /* A malformed feed must not disable local reminders. */ }
        candidates.sort(Comparator.comparingLong(candidate->candidate.occurrence.start));
        for(Candidate candidate:candidates) {
            if(scheduled.contains(candidate.key))continue;
            long occurrenceStart=candidate.occurrence.start;
            if(deliveredAt(delivered,candidate.key)==occurrenceStart||ReminderSchedule.stale(occurrenceStart,now))continue;
            long trigger=ReminderTime.trigger(candidate.occurrence,zone,candidate.minutes);
            String fingerprint=fingerprint(candidate.event); String suffix=prefKey(candidate.key);
            PendingIntent intent=PendingIntent.getBroadcast(c,0,alarmIntent(c,candidate.key).putExtra("occurrence",occurrenceStart).putExtra("trigger",trigger).putExtra("fingerprint",fingerprint),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            long alarmTime=Math.max(trigger,now+1000);
            try { if(exactAllowed(c))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alarmTime,intent); else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alarmTime,intent); }
            catch(SecurityException revoked) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alarmTime,intent); }
            scheduled.add(candidate.key); editor.putLong("start_"+suffix,occurrenceStart).putLong("trigger_"+suffix,trigger).putString("fingerprint_"+suffix,fingerprint);
        }
        editor.putStringSet("ids",scheduled).apply(); scheduleDateRefresh(c);
    }
    public static void scheduleDateRefresh(Context c) {
        long next=LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        PendingIntent intent=PendingIntent.getBroadcast(c,2026,new Intent(c,RefreshReceiver.class).setAction("com.amphoreus.calendar.NEW_DAY"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,intent);
    }
    private Reminders() {}
}
