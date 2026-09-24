package com.amphoreus.calendar.data;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import com.amphoreus.calendar.core.CalendarEvent;
import java.util.*;

public final class LocalStore extends SQLiteOpenHelper {
    public LocalStore(Context context) { super(context,"calendar.db",null,4); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,location TEXT NOT NULL,notes TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,zone TEXT NOT NULL,all_day INTEGER NOT NULL,recurrence TEXT NOT NULL,reminder INTEGER NOT NULL,reminder_art INTEGER NOT NULL DEFAULT -1,color INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE system_reminders (event_id INTEGER PRIMARY KEY,reminder INTEGER NOT NULL,reminder_art INTEGER NOT NULL DEFAULT -1)");
        createIcsTables(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2)db.execSQL("ALTER TABLE events ADD COLUMN reminder_art INTEGER NOT NULL DEFAULT -1");
        if(oldVersion<3)db.execSQL("CREATE TABLE system_reminders (event_id INTEGER PRIMARY KEY,reminder INTEGER NOT NULL,reminder_art INTEGER NOT NULL DEFAULT -1)");
        if(oldVersion<4)createIcsTables(db);
    }
    public List<CalendarEvent> all() {
        List<CalendarEvent> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("events",null,null,null,null,null,"start ASC")) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    public CalendarEvent get(long id) {
        try(Cursor c=getReadableDatabase().query("events",null,"_id=?",new String[]{String.valueOf(id)},null,null,null)) { return c.moveToFirst() ? read(c) : null; }
    }
    public long save(CalendarEvent e) {
        e.validate(); ContentValues v=new ContentValues();
        v.put("title",e.title.trim()); v.put("location",e.location); v.put("notes",e.notes); v.put("start",e.start); v.put("end",e.end);
        v.put("zone",e.zone); v.put("all_day",e.allDay?1:0); v.put("recurrence",e.recurrence); v.put("reminder",e.reminderMinutes); v.put("reminder_art",e.reminderArt); v.put("color",e.color);
        if(e.id==0) return getWritableDatabase().insertOrThrow("events",null,v);
        if(getWritableDatabase().update("events",v,"_id=?",new String[]{String.valueOf(e.id)})!=1) throw new IllegalStateException("此日程已被删除，请返回刷新");
        return e.id;
    }
    public void delete(long id) { getWritableDatabase().delete("events","_id=?",new String[]{String.valueOf(id)}); }
    private CalendarEvent read(Cursor c) {
        CalendarEvent e=new CalendarEvent(); e.id=c.getLong(c.getColumnIndexOrThrow("_id"));
        e.title=c.getString(c.getColumnIndexOrThrow("title")); e.location=c.getString(c.getColumnIndexOrThrow("location")); e.notes=c.getString(c.getColumnIndexOrThrow("notes"));
        e.start=c.getLong(c.getColumnIndexOrThrow("start")); e.end=c.getLong(c.getColumnIndexOrThrow("end")); e.zone=c.getString(c.getColumnIndexOrThrow("zone"));
        e.allDay=c.getInt(c.getColumnIndexOrThrow("all_day"))==1; e.recurrence=c.getString(c.getColumnIndexOrThrow("recurrence"));
        e.reminderMinutes=c.getInt(c.getColumnIndexOrThrow("reminder")); e.appReminderMinutes=e.reminderMinutes; e.reminderArt=c.getInt(c.getColumnIndexOrThrow("reminder_art")); e.color=c.getInt(c.getColumnIndexOrThrow("color")); return e;
    }
    public AppReminder systemReminder(long eventId) {
        try(Cursor c=getReadableDatabase().query("system_reminders",new String[]{"reminder","reminder_art"},"event_id=?",new String[]{String.valueOf(eventId)},null,null,null)) {
            return c.moveToFirst()?new AppReminder(c.getInt(0),c.getInt(1)):null;
        }
    }
    public Map<Long,AppReminder> systemReminders() {
        Map<Long,AppReminder> out=new HashMap<>();
        try(Cursor c=getReadableDatabase().query("system_reminders",new String[]{"event_id","reminder","reminder_art"},null,null,null,null,null)) {
            while(c.moveToNext())out.put(c.getLong(0),new AppReminder(c.getInt(1),c.getInt(2)));
        }
        return out;
    }
    public void saveSystemReminder(long eventId,int minutes,int art) {
        SQLiteDatabase db=getWritableDatabase();
        ContentValues values=new ContentValues(); values.put("event_id",eventId); values.put("reminder",minutes); values.put("reminder_art",art);
        db.insertWithOnConflict("system_reminders",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void deleteSystemReminder(long eventId) { getWritableDatabase().delete("system_reminders","event_id=?",new String[]{String.valueOf(eventId)}); }
    public List<IcsFeed> icsFeeds() {
        List<IcsFeed> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("ics_feeds",null,null,null,null,null,"_id ASC")) {
            while(c.moveToNext())out.add(new IcsFeed(c.getLong(c.getColumnIndexOrThrow("_id")),c.getString(c.getColumnIndexOrThrow("url")),c.getString(c.getColumnIndexOrThrow("name")),c.getInt(c.getColumnIndexOrThrow("enabled"))==1,c.getLong(c.getColumnIndexOrThrow("last_sync")),c.getString(c.getColumnIndexOrThrow("last_error"))));
        }
        return out;
    }
    public IcsFeed addIcsFeed(String url) {
        SQLiteDatabase db=getWritableDatabase(); ContentValues values=new ContentValues(); values.put("url",url); values.put("name","ICS 订阅"); values.put("enabled",1); values.put("last_sync",0); values.put("last_error","");
        db.insertWithOnConflict("ics_feeds",null,values,SQLiteDatabase.CONFLICT_IGNORE);
        try(Cursor c=db.query("ics_feeds",null,"url=?",new String[]{url},null,null,null)) {
            if(c.moveToFirst())return new IcsFeed(c.getLong(c.getColumnIndexOrThrow("_id")),c.getString(c.getColumnIndexOrThrow("url")),c.getString(c.getColumnIndexOrThrow("name")),c.getInt(c.getColumnIndexOrThrow("enabled"))==1,c.getLong(c.getColumnIndexOrThrow("last_sync")),c.getString(c.getColumnIndexOrThrow("last_error")));
        }
        throw new IllegalStateException("无法保存 ICS 订阅");
    }
    public void removeIcsFeed(long feedId) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            try(Cursor c=db.query("ics_events",new String[]{"_id"},"feed_id=?",new String[]{String.valueOf(feedId)},null,null,null)) {
                while(c.moveToNext())db.delete("system_reminders","event_id=?",new String[]{String.valueOf(-c.getLong(0))});
            }
            db.delete("ics_events","feed_id=?",new String[]{String.valueOf(feedId)}); db.delete("ics_feeds","_id=?",new String[]{String.valueOf(feedId)}); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void updateIcsFeedStatus(long feedId,long lastSync,String error) {
        ContentValues values=new ContentValues(); values.put("last_sync",lastSync); values.put("last_error",error==null?"":error); getWritableDatabase().update("ics_feeds",values,"_id=?",new String[]{String.valueOf(feedId)});
    }
    public void replaceIcsEvents(long feedId,String feedName,List<CalendarEvent> events) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Map<String,Long> existing=new HashMap<>();
            try(Cursor c=db.query("ics_events",new String[]{"_id","uid"},"feed_id=?",new String[]{String.valueOf(feedId)},null,null,null)) { while(c.moveToNext())existing.put(c.getString(1),c.getLong(0)); }
            Set<String> seen=new HashSet<>();
            for(CalendarEvent event:events) {
                if(event==null||event.importUid==null||event.importUid.isEmpty()||!seen.add(event.importUid))continue;
                event.imported=true; event.system=true; event.writable=false; event.calendarId=-2; event.calendarName=feedName;
                ContentValues values=icsValues(feedId,event); Long rowId=existing.get(event.importUid);
                if(rowId==null)rowId=db.insertOrThrow("ics_events",null,values); else db.update("ics_events",values,"_id=?",new String[]{String.valueOf(rowId)});
                event.id=-rowId;
            }
            for(Map.Entry<String,Long> old:existing.entrySet())if(!seen.contains(old.getKey())) {
                db.delete("system_reminders","event_id=?",new String[]{String.valueOf(-old.getValue())}); db.delete("ics_events","_id=?",new String[]{String.valueOf(old.getValue())});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public CalendarEvent getIcs(long eventId) {
        if(eventId>=0)return null;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.* FROM ics_events e JOIN ics_feeds f ON f._id=e.feed_id WHERE e._id=? AND f.enabled=1",new String[]{String.valueOf(-eventId)})) { return c.moveToFirst()?readIcs(c):null; }
    }
    public List<CalendarEvent> allIcs() {
        List<CalendarEvent> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.* FROM ics_events e JOIN ics_feeds f ON f._id=e.feed_id WHERE f.enabled=1 ORDER BY e.start ASC",null)) { while(c.moveToNext())out.add(readIcs(c)); }
        return out;
    }
    private static ContentValues icsValues(long feedId,CalendarEvent e) {
        ContentValues v=new ContentValues(); v.put("feed_id",feedId); v.put("uid",e.importUid); v.put("title",e.title); v.put("location",e.location); v.put("notes",e.notes); v.put("start",e.start); v.put("end",e.end); v.put("zone",e.zone); v.put("all_day",e.allDay?1:0); v.put("recurrence",e.recurrence); v.put("color",e.color); return v;
    }
    private static CalendarEvent readIcs(Cursor c) {
        CalendarEvent e=new CalendarEvent(); e.id=-c.getLong(c.getColumnIndexOrThrow("_id")); e.system=true; e.imported=true; e.writable=false; e.calendarId=-2; e.calendarName="ICS 订阅";
        e.importUid=c.getString(c.getColumnIndexOrThrow("uid")); e.title=c.getString(c.getColumnIndexOrThrow("title")); e.location=c.getString(c.getColumnIndexOrThrow("location")); e.notes=c.getString(c.getColumnIndexOrThrow("notes"));
        e.start=c.getLong(c.getColumnIndexOrThrow("start")); e.end=c.getLong(c.getColumnIndexOrThrow("end")); e.zone=c.getString(c.getColumnIndexOrThrow("zone")); e.allDay=c.getInt(c.getColumnIndexOrThrow("all_day"))==1; e.recurrence=c.getString(c.getColumnIndexOrThrow("recurrence"));
        e.reminderMinutes=-1; e.appReminderMinutes=-1; e.reminderArt=-1; e.color=c.getInt(c.getColumnIndexOrThrow("color")); return e;
    }
    private static void createIcsTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ics_feeds (_id INTEGER PRIMARY KEY AUTOINCREMENT,url TEXT NOT NULL UNIQUE,name TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 1,last_sync INTEGER NOT NULL DEFAULT 0,last_error TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE IF NOT EXISTS ics_events (_id INTEGER PRIMARY KEY AUTOINCREMENT,feed_id INTEGER NOT NULL,uid TEXT NOT NULL,title TEXT NOT NULL,location TEXT NOT NULL,notes TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,zone TEXT NOT NULL,all_day INTEGER NOT NULL,recurrence TEXT NOT NULL,color INTEGER NOT NULL,UNIQUE(feed_id,uid))");
    }
    public static final class IcsFeed {
        public final long id,lastSync; public final String url,name,lastError; public final boolean enabled;
        public IcsFeed(long id,String url,String name,boolean enabled,long lastSync,String lastError) { this.id=id; this.url=url; this.name=name; this.enabled=enabled; this.lastSync=lastSync; this.lastError=lastError==null?"":lastError; }
        public String host() { try { return new java.net.URL(url).getHost(); } catch(Exception ignored) { return "ICS"; } }
    }
    public static final class AppReminder {
        public final int minutes,art;
        public AppReminder(int minutes,int art) { this.minutes=minutes; this.art=art; }
    }
}
