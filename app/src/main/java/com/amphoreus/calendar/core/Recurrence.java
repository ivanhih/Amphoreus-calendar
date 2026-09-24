package com.amphoreus.calendar.core;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Deliberately supports only the five recurrence choices exposed by our editor. */
public final class Recurrence {
    public static final String[] RULES={"","FREQ=DAILY","FREQ=WEEKLY","FREQ=MONTHLY","FREQ=YEARLY"};
    public static final String[] LABELS={"不重复","每天","每周","每月","每年"};
    public static boolean supported(String rule) { return Arrays.asList(RULES).contains(rule); }
    public static List<Occurrence> expand(CalendarEvent e, LocalDate from, LocalDate until, ZoneId displayZone) {
        List<Occurrence> out=new ArrayList<>();
        if (!until.isAfter(from)) return out;
        if(e.recurrence.isEmpty()) { Occurrence o=new Occurrence(e,e.start,e.end); if(o.overlaps(from,until,displayZone)) out.add(o); return out; }
        if(!supported(e.recurrence)) throw new IllegalArgumentException("不支持的本地重复规则");
        ZoneId zone=e.allDay ? ZoneOffset.UTC : ZoneId.of(e.zone);
        ZonedDateTime anchor=Instant.ofEpochMilli(e.start).atZone(zone);
        long duration=e.end-e.start;
        LocalDate lower=from.minusDays(duration/86400000L+2);
        long i;
        switch(e.recurrence) {
            case "FREQ=DAILY": i=ChronoUnit.DAYS.between(anchor.toLocalDate(),lower); break;
            case "FREQ=WEEKLY": i=ChronoUnit.WEEKS.between(anchor.toLocalDate(),lower); break;
            case "FREQ=MONTHLY": i=ChronoUnit.MONTHS.between(YearMonth.from(anchor),YearMonth.from(lower)); break;
            default: i=lower.getYear()-anchor.getYear();
        }
        i=Math.max(0,i-1);
        for(;;i++) {
            LocalDate candidate;
            switch(e.recurrence) {
                case "FREQ=DAILY": candidate=anchor.toLocalDate().plusDays(i); break;
                case "FREQ=WEEKLY": candidate=anchor.toLocalDate().plusWeeks(i); break;
                case "FREQ=MONTHLY": {
                    YearMonth m=YearMonth.from(anchor).plusMonths(i);
                    if(m.atDay(1).isAfter(until.plusDays(2))) return out;
                    if(anchor.getDayOfMonth()>m.lengthOfMonth()) continue;
                    candidate=m.atDay(anchor.getDayOfMonth()); break;
                }
                default: {
                    int year=Math.toIntExact(anchor.getYear()+i);
                    if(year>until.getYear()+1) return out;
                    if(anchor.getMonthValue()==2 && anchor.getDayOfMonth()==29 && !Year.isLeap(year)) continue;
                    candidate=LocalDate.of(year,anchor.getMonthValue(),anchor.getDayOfMonth());
                }
            }
            if(candidate.isAfter(until.plusDays(2))) return out;
            long start=candidate.atTime(anchor.toLocalTime()).atZone(zone).toInstant().toEpochMilli();
            Occurrence o=new Occurrence(e,start,start+duration);
            if(o.overlaps(from,until,displayZone)) out.add(o);
        }
    }
    private Recurrence() {}
}
