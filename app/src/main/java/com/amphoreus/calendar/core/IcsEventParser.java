package com.amphoreus.calendar.core;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Dependency-free parser for the VEVENT subset used by calendar subscription feeds. */
public final class IcsEventParser {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter SHORT_DATE_TIME=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm");

    private static final class Property {
        final String parameters,value;
        Property(String parameters,String value) { this.parameters=parameters; this.value=value; }
    }
    private static final class ParsedTime {
        final long millis;
        final boolean allDay;
        final ZoneId zone;
        ParsedTime(long millis,boolean allDay,ZoneId zone) { this.millis=millis; this.allDay=allDay; this.zone=zone; }
    }

    public static List<CalendarEvent> parse(String text) {
        if(text==null||text.trim().isEmpty()) throw new IllegalArgumentException("ICS 订阅内容为空");
        List<CalendarEvent> out=new ArrayList<>();
        Map<String,Property> properties=null;
        int nested=0;
        for(String raw:unfold(text)) {
            String line=raw.trim();
            if(line.equalsIgnoreCase("BEGIN:VEVENT")) { properties=new LinkedHashMap<>(); nested=0; continue; }
            if(properties==null)continue;
            if(line.equalsIgnoreCase("END:VEVENT")) {
                CalendarEvent event=parseEvent(properties); if(event!=null)out.add(event);
                properties=null; nested=0; continue;
            }
            if(line.regionMatches(true,0,"BEGIN:",0,6)) { nested++; continue; }
            if(line.regionMatches(true,0,"END:",0,4)) { if(nested>0)nested--; continue; }
            if(nested>0)continue;
            int colon=line.indexOf(':'); if(colon<=0)continue;
            String left=line.substring(0,colon); String name=left;
            int semicolon=left.indexOf(';'); if(semicolon>=0)name=left.substring(0,semicolon);
            properties.put(name.toUpperCase(Locale.ROOT),new Property(semicolon<0?"":left.substring(semicolon+1),line.substring(colon+1)));
        }
        return out;
    }

    private static CalendarEvent parseEvent(Map<String,Property> p) {
        Property startProperty=p.get("DTSTART"); if(startProperty==null)return null;
        ParsedTime start=parseTime(startProperty); if(start==null)return null;
        Property endProperty=p.get("DTEND"); ParsedTime end=endProperty==null?null:parseTime(endProperty);
        long endMillis=end==null?start.millis+defaultDuration(start.allDay):end.millis;
        if(endMillis<=start.millis)endMillis=start.millis+defaultDuration(start.allDay);
        String uid=value(p,"UID"); if(uid.isEmpty())uid=UUID.nameUUIDFromBytes((value(p,"SUMMARY")+'|'+start.millis).getBytes(StandardCharsets.UTF_8)).toString();
        CalendarEvent event=new CalendarEvent();
        event.id=0; event.system=true; event.imported=true; event.writable=false; event.calendarId=-2;
        event.importUid=unescape(uid); event.title=unescape(value(p,"SUMMARY")); if(event.title.isEmpty())event.title="未命名日程";
        event.location=unescape(value(p,"LOCATION")); event.notes=unescape(value(p,"DESCRIPTION")); event.start=start.millis; event.end=endMillis;
        event.zone=start.zone.getId(); event.allDay=start.allDay; event.recurrence=simpleRecurrence(value(p,"RRULE"));
        event.reminderMinutes=-1; event.appReminderMinutes=CalendarEvent.DEFAULT_IMPORTED_REMINDER_MINUTES; event.reminderArt=CalendarEvent.RANDOM_ART;
        return event;
    }

    private static long defaultDuration(boolean allDay) { return (allDay?86400000L:3600000L); }

    private static ParsedTime parseTime(Property property) {
        String value=property.value.trim(); String parameters=property.parameters.toUpperCase(Locale.ROOT);
        try {
            if(value.matches("[0-9]{8}"))return new ParsedTime(LocalDate.parse(value,DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),true,ZoneOffset.UTC);
            ZoneId zone=zone(parameters);
            if(value.endsWith("Z")) { String body=value.substring(0,value.length()-1); return new ParsedTime(parseLocal(body).atZone(ZoneOffset.UTC).toInstant().toEpochMilli(),false,ZoneOffset.UTC); }
            return new ParsedTime(parseLocal(value).atZone(zone).toInstant().toEpochMilli(),false,zone);
        } catch(DateTimeException|IllegalArgumentException ex) { return null; }
    }

    private static LocalDateTime parseLocal(String value) {
        try { return LocalDateTime.parse(value,DATE_TIME); }
        catch(DateTimeParseException ignored) { return LocalDateTime.parse(value,SHORT_DATE_TIME); }
    }

    private static ZoneId zone(String parameters) {
        for(String parameter:parameters.split(";")) {
            int equals=parameter.indexOf('='); if(equals<0)continue;
            if(!parameter.substring(0,equals).equalsIgnoreCase("TZID"))continue;
            String id=parameter.substring(equals+1).replace("\"","");
            try { return ZoneId.of(id); } catch(DateTimeException ignored) { }
        }
        return ZoneId.systemDefault();
    }

    private static String simpleRecurrence(String raw) {
        if(raw==null||raw.trim().isEmpty())return "";
        String frequency="";
        for(String part:raw.split(";")) {
            String[] pair=part.split("=",2); if(pair.length!=2) return "";
            String name=pair[0].toUpperCase(Locale.ROOT),value=pair[1].toUpperCase(Locale.ROOT);
            if(name.equals("FREQ") && (value.equals("DAILY")||value.equals("WEEKLY")||value.equals("MONTHLY")||value.equals("YEARLY")))frequency=value;
            else if(!name.equals("INTERVAL")||!value.equals("1"))return "";
        }
        return frequency.isEmpty()?"":"FREQ="+frequency;
    }

    private static String value(Map<String,Property> properties,String name) {
        Property property=properties.get(name); return property==null?"":property.value;
    }

    private static String unescape(String value) {
        return value.replace("\\n","\n").replace("\\N","\n").replace("\\,",",").replace("\\;",";").replace("\\\\","\\");
    }

    private static List<String> unfold(String text) {
        String normalized=text.replace("\r\n","\n").replace('\r','\n'); List<String> out=new ArrayList<>();
        for(String line:normalized.split("\n",-1)) {
            if((line.startsWith(" ")||line.startsWith("\t"))&&!out.isEmpty())out.set(out.size()-1,out.get(out.size()-1)+line.substring(1));
            else out.add(line);
        }
        return out;
    }

    private IcsEventParser() {}
}
