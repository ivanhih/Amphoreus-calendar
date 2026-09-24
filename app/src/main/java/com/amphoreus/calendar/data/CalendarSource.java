package com.amphoreus.calendar.data;

public final class CalendarSource {
    public final long id;
    public final String name, account;
    public final int color, maxReminders;
    public final boolean writable, alertSupported;
    public CalendarSource(long id, String name, String account, int color, boolean writable, int maxReminders, boolean alertSupported) {
        this.id=id; this.name=name; this.account=account; this.color=color; this.writable=writable;
        this.maxReminders=maxReminders; this.alertSupported=alertSupported;
    }
    public static CalendarSource local() { return new CalendarSource(-1,"本地日历","仅保存在此应用",0xffdec38d,true,1,true); }
    @Override public String toString() { return name+(id==-1 ? " · 本地" : " · "+account)+(writable ? "" : " · 只读"); }
}
