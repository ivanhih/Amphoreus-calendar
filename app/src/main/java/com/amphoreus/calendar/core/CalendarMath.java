package com.amphoreus.calendar.core;

import java.time.*;
import java.util.*;

public final class CalendarMath {
    private CalendarMath() {}
    public static LocalDate gridStart(YearMonth month, boolean mondayFirst) {
        LocalDate first=month.atDay(1);
        int offset=mondayFirst ? first.getDayOfWeek().getValue()-1 : first.getDayOfWeek().getValue()%7;
        return first.minusDays(offset);
    }
    public static List<LocalDate> grid(YearMonth month, boolean mondayFirst) {
        List<LocalDate> days=new ArrayList<>(); LocalDate start=gridStart(month,mondayFirst);
        for(int i=0;i<42;i++) days.add(start.plusDays(i));
        return days;
    }
    /** 本月 1 日落在网格的第几列（0 起）。 */
    public static int leadColumn(YearMonth month, boolean mondayFirst) {
        return (month.atDay(1).getDayOfWeek().getValue()-(mondayFirst?1:0)+7)%7;
    }
    /**
     * 渲染这个月需要几行格子，最多 6 行。
     *
     * 必须用 leadColumn 而不是"grid 第一格的列号"——后者恒为 0，于是行数只看月份天数，
     * 当月首日靠后（周日开头、31 天）时最后一行会被整行丢掉：2026 年 2 月只画到 22 日，
     * 11 月只画到 29 日，那些日期在首页上根本点不到。这个 bug 在旧版就存在，因为一直只
     * 拿 9 月做验证，而 9 月恰好不受影响。
     */
    public static int rows(YearMonth month, boolean mondayFirst) {
        return Math.min(6,(leadColumn(month,mondayFirst)+month.lengthOfMonth()+6)/7);
    }
    public static LocalDate weekStart(LocalDate date, boolean mondayFirst) {
        return date.minusDays(mondayFirst ? date.getDayOfWeek().getValue()-1 : date.getDayOfWeek().getValue()%7);
    }
    public static long midnightUtc(LocalDate date) { return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(); }
    public static LocalDateTime defaultStart(LocalDate selected,LocalDateTime now) {
        if(selected.equals(now.toLocalDate())) return now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        return selected.atTime(9,0);
    }
}
