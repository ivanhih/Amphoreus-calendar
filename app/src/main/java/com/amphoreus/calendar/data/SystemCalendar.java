package com.amphoreus.calendar.data;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.*;
import com.amphoreus.calendar.core.*;
import java.time.*;
import java.util.*;

/** Normal application access: sync adapters remain owned by the device's calendar accounts. */
public final class SystemCalendar {
    private final Context context;
    private final ContentResolver resolver;
    public SystemCalendar(Context context) { this.context=context; resolver=context.getContentResolver(); }
    public boolean canRead() { return context.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED; }
    public boolean canWrite() { return canRead() && context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED; }
    /** Android requires one of the calendar permissions even to register a provider observer. */
    public boolean canObserve() {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED
            || context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;
    }
    public List<CalendarSource> sources() {
        List<CalendarSource> out=new ArrayList<>(); if(!canRead()) return out;
        String[] projection={Calendars._ID,Calendars.CALENDAR_DISPLAY_NAME,Calendars.ACCOUNT_NAME,Calendars.CALENDAR_COLOR,Calendars.CALENDAR_ACCESS_LEVEL,Calendars.MAX_REMINDERS,Calendars.ALLOWED_REMINDERS};
        try(Cursor c=resolver.query(Calendars.CONTENT_URI,projection,null,null,Calendars.CALENDAR_DISPLAY_NAME+" ASC")) {
            if(c==null) throw new IllegalStateException("无法读取手机日历，请稍后重试");
            while(c.moveToNext()) {
                String methods=c.getString(6);
                boolean alerts=methods==null || Arrays.asList(methods.split(",")).contains(String.valueOf(Reminders.METHOD_ALERT));
                out.add(new CalendarSource(c.getLong(0),text(c,1),text(c,2),c.getInt(3),canWrite() && c.getInt(4)>=Calendars.CAL_ACCESS_CONTRIBUTOR,c.getInt(5),alerts));
            }
        }
        return out;
    }
    public List<Occurrence> between(LocalDate from,LocalDate until,ZoneId zone,Set<String> hidden) {
        List<Occurrence> out=new ArrayList<>(); if(!canRead()) return out;
        Map<Long,CalendarSource> sources=new HashMap<>(); for(CalendarSource source:sources()) sources.put(source.id,source);
        Uri.Builder uri=Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri,from.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli());
        ContentUris.appendId(uri,until.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli());
        String[] projection={Instances.EVENT_ID,Instances.BEGIN,Instances.END,Instances.TITLE,Instances.ALL_DAY,Instances.EVENT_LOCATION,Instances.DESCRIPTION,Instances.CALENDAR_ID,Instances.EVENT_TIMEZONE,Instances.RRULE};
        try(Cursor c=resolver.query(uri.build(),projection,Events.DELETED+"=0 AND ("+Events.STATUS+" IS NULL OR "+Events.STATUS+"!=?)",new String[]{String.valueOf(Events.STATUS_CANCELED)},Instances.BEGIN+" ASC")) {
            if(c==null) throw new IllegalStateException("无法读取系统日程");
            while(c.moveToNext()) {
                long calendarId=c.getLong(7); if(hidden.contains(String.valueOf(calendarId))) continue;
                CalendarEvent e=new CalendarEvent(); e.system=true; e.id=c.getLong(0); e.start=c.getLong(1); e.end=c.getLong(2);
                e.title=text(c,3); if(e.title.isEmpty()) e.title="未命名日程";
                e.allDay=c.getInt(4)==1; e.location=text(c,5); e.notes=text(c,6); e.calendarId=calendarId; e.zone=text(c,8); e.recurrence=text(c,9);
                CalendarSource source=sources.get(calendarId); if(source!=null) { e.color=source.color; e.calendarName=source.name; e.writable=source.writable; }
                Occurrence o=new Occurrence(e,e.start,e.end); if(o.overlaps(from,until,zone)) out.add(o);
            }
        }
        return out;
    }
    public CalendarEvent get(long id) {
        if(!canRead()) throw new SecurityException("请先授权读取系统日历");
        String[] p={Events._ID,Events.CALENDAR_ID,Events.TITLE,Events.EVENT_LOCATION,Events.DESCRIPTION,Events.DTSTART,Events.DTEND,Events.DURATION,Events.ALL_DAY,Events.EVENT_TIMEZONE,Events.RRULE,Events.RDATE,Events.EXDATE,Events.ORIGINAL_ID,Events.HAS_ATTENDEE_DATA,Events.EXRULE};
        CalendarEvent e=new CalendarEvent();
        try(Cursor c=resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI,id),p,Events.DELETED+"=0",null,null)) {
            if(c==null || !c.moveToFirst()) return null;
            e.system=true; e.id=id; e.calendarId=c.getLong(1); e.title=text(c,2); e.location=text(c,3); e.notes=text(c,4); e.start=c.getLong(5);
            e.end=c.isNull(6)?e.start+parseDuration(text(c,7)):c.getLong(6); e.allDay=c.getInt(8)==1; e.zone=text(c,9);
            if(e.zone.isEmpty()) e.zone=ZoneId.systemDefault().getId();
            e.recurrence=text(c,10);
            e.advanced=!Recurrence.supported(e.recurrence)||!text(c,11).isEmpty()||!text(c,12).isEmpty()||!c.isNull(13)||c.getInt(14)==1||!text(c,15).isEmpty();
        }
        e.writable=false;
        for(CalendarSource source:sources()) if(source.id==e.calendarId) { e.calendarName=source.name; e.color=source.color; e.writable=source.writable; }
        e.reminderMinutes=-1;
        try(Cursor c=resolver.query(Reminders.CONTENT_URI,new String[]{Reminders.MINUTES,Reminders.METHOD},Reminders.EVENT_ID+"=?",new String[]{String.valueOf(id)},null)) {
            if(c!=null) { int count=0; while(c.moveToNext()) { if(count++==0) e.reminderMinutes=c.getInt(0); if(c.getInt(1)!=Reminders.METHOD_ALERT) e.advanced=true; } if(count>1) e.advanced=true; }
        }
        // Series with detached exceptions need the system editor to preserve those exceptions.
        if(!e.recurrence.isEmpty()) try(Cursor c=resolver.query(Events.CONTENT_URI,new String[]{Events._ID},Events.ORIGINAL_ID+"=? AND "+Events.DELETED+"=0",new String[]{String.valueOf(id)},null)) { if(c!=null && c.moveToFirst()) e.advanced=true; }
        return e;
    }
    public long save(CalendarEvent e) throws Exception {
        e.validate(); if(!canWrite()) throw new SecurityException("请授予系统日历读写权限");
        CalendarSource source=null; for(CalendarSource s:sources()) if(s.id==e.calendarId) source=s;
        if(source==null || !source.writable) throw new IllegalArgumentException("所选日历不可写，请选择其他日历");
        if(e.reminderMinutes>=0 && (source.maxReminders<1 || !source.alertSupported)) throw new IllegalArgumentException("此账号日历不支持通知提醒，请选择不提醒或其他日历");
        if(e.id!=0) {
            CalendarEvent current=get(e.id);
            if(current==null) throw new IllegalStateException("此日程已被删除");
            if(!current.writable || current.advanced) throw new IllegalStateException("请在系统日历中编辑这条日程");
            if(current.calendarId!=e.calendarId) throw new IllegalArgumentException("已有系统日程不能直接更换所属日历");
        }
        ContentValues v=new ContentValues(); v.put(Events.TITLE,e.title.trim()); v.put(Events.EVENT_LOCATION,e.location); v.put(Events.DESCRIPTION,e.notes);
        v.put(Events.DTSTART,e.start); v.put(Events.ALL_DAY,e.allDay?1:0); v.put(Events.EVENT_TIMEZONE,e.allDay?"UTC":e.zone);
        v.put(Events.EVENT_END_TIMEZONE,e.allDay?"UTC":e.zone); v.put(Events.HAS_ALARM,e.reminderMinutes>=0?1:0);
        if(e.recurrence.isEmpty()) { v.putNull(Events.RRULE); v.putNull(Events.DURATION); v.put(Events.DTEND,e.end); }
        else { v.put(Events.RRULE,e.recurrence); v.putNull(Events.DTEND); v.put(Events.DURATION,e.allDay?"P"+((e.end-e.start)/86400000L)+"D":"P"+((e.end-e.start)/1000)+"S"); }
        ArrayList<ContentProviderOperation> ops=new ArrayList<>();
        if(e.id==0) { v.put(Events.CALENDAR_ID,e.calendarId); ops.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(v).build()); }
        else {
            ops.add(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI,e.id)).withValues(v).withExpectedCount(1).build());
            ops.add(ContentProviderOperation.newDelete(Reminders.CONTENT_URI).withSelection(Reminders.EVENT_ID+"=?",new String[]{String.valueOf(e.id)}).build());
        }
        if(e.reminderMinutes>=0) {
            ContentProviderOperation.Builder reminder=ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValue(Reminders.MINUTES,e.reminderMinutes).withValue(Reminders.METHOD,Reminders.METHOD_ALERT);
            if(e.id==0) reminder.withValueBackReference(Reminders.EVENT_ID,0); else reminder.withValue(Reminders.EVENT_ID,e.id);
            ops.add(reminder.build());
        }
        ContentProviderResult[] results=resolver.applyBatch(CalendarContract.AUTHORITY,ops);
        return e.id==0?ContentUris.parseId(results[0].uri):e.id;
    }
    public void delete(long id) {
        CalendarEvent e=get(id); if(e==null) return;
        if(!canWrite() || !e.writable || e.advanced) throw new SecurityException("请在系统日历中管理这条日程");
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI,id),null,null);
    }
    private static long parseDuration(String text) {
        if(text.matches("P[0-9]+S")) return Long.parseLong(text.substring(1,text.length()-1))*1000L;
        if(text.matches("P[0-9]+W")) return Long.parseLong(text.substring(1,text.length()-1))*7*86400000L;
        try { return Duration.parse(text).toMillis(); } catch(Exception ex) { throw new IllegalStateException("此事件时长需由系统日历处理",ex); }
    }
    private static String text(Cursor c,int index) { String value=c.getString(index); return value==null?"":value; }
}
