package com.amphoreus.calendar.data;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import com.amphoreus.calendar.core.CalendarEvent;
import java.util.*;

public final class LocalStore extends SQLiteOpenHelper {
    public LocalStore(Context context) { super(context,"calendar.db",null,3); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,location TEXT NOT NULL,notes TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,zone TEXT NOT NULL,all_day INTEGER NOT NULL,recurrence TEXT NOT NULL,reminder INTEGER NOT NULL,reminder_art INTEGER NOT NULL DEFAULT -1,color INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE system_reminders (event_id INTEGER PRIMARY KEY,reminder INTEGER NOT NULL,reminder_art INTEGER NOT NULL DEFAULT -1)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2)db.execSQL("ALTER TABLE events ADD COLUMN reminder_art INTEGER NOT NULL DEFAULT -1");
        if(oldVersion<3)db.execSQL("CREATE TABLE system_reminders (event_id INTEGER PRIMARY KEY,reminder INTEGER NOT NULL,reminder_art INTEGER NOT NULL DEFAULT -1)");
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
    public static final class AppReminder {
        public final int minutes,art;
        public AppReminder(int minutes,int art) { this.minutes=minutes; this.art=art; }
    }
}
