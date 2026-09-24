package com.amphoreus.calendar.data;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.*;
import com.amphoreus.calendar.core.CalendarEvent;
import com.amphoreus.calendar.core.IcsEventParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Downloads user-configured ICS feeds into the private app database without logging their URLs or events. */
public final class IcsSync {
    public static final int JOB_ID=202610;
    private static final long PERIOD_MILLIS=6L*60L*60L*1000L;
    private static final int MAX_BYTES=5*1024*1024;

    public static LocalStore.IcsFeed addFeed(Context c,String rawUrl) {
        return new LocalStore(c).addIcsFeed(validateUrl(rawUrl));
    }
    public static String validateUrl(String rawUrl) {
        String value=rawUrl==null?"":rawUrl.trim();
        try {
            URL url=new URL(value);
            if(!"https".equalsIgnoreCase(url.getProtocol())||url.getHost().isEmpty())throw new IllegalArgumentException("ICS 订阅必须使用 HTTPS 链接");
        } catch(MalformedURLException ex) { throw new IllegalArgumentException("请输入有效的 ICS HTTPS 链接"); }
        return value;
    }
    public static int syncAll(Context c) {
        LocalStore store=new LocalStore(c); int success=0;
        for(LocalStore.IcsFeed feed:store.icsFeeds()) {
            if(!feed.enabled)continue;
            try { syncFeed(store,feed); success++; }
            catch(Exception ignored) { store.updateIcsFeedStatus(feed.id,feed.lastSync,"同步失败，请检查链接或网络"); }
        }
        return success;
    }
    public static boolean syncFeed(Context c,long feedId) {
        LocalStore store=new LocalStore(c);
        for(LocalStore.IcsFeed feed:store.icsFeeds())if(feed.id==feedId) {
            try { syncFeed(store,feed); return true; }
            catch(Exception ignored) { store.updateIcsFeedStatus(feed.id,feed.lastSync,"同步失败，请检查链接或网络"); return false; }
        }
        return false;
    }
    private static void syncFeed(LocalStore store,LocalStore.IcsFeed feed) throws IOException {
        String payload=download(feed.url);
        if(!payload.toUpperCase(Locale.ROOT).contains("BEGIN:VCALENDAR"))throw new IOException("ICS 内容格式不正确");
        List<CalendarEvent> events=IcsEventParser.parse(payload);
        store.replaceIcsEvents(feed.id,feed.name+" · "+feed.host(),events);
        store.updateIcsFeedStatus(feed.id,System.currentTimeMillis(),"");
    }
    private static String download(String rawUrl) throws IOException {
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL(rawUrl).openConnection(); connection.setRequestMethod("GET"); connection.setConnectTimeout(15000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept","text/calendar, text/plain;q=0.9, */*;q=0.1"); connection.setRequestProperty("User-Agent","AmphoreusCalendar/1.0");
            int code=connection.getResponseCode(); if(code<200||code>=300)throw new IOException("ICS HTTP status "+code);
            try(InputStream input=connection.getInputStream(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int read,total=0;
                while((read=input.read(buffer))!=-1) { total+=read; if(total>MAX_BYTES)throw new IOException("ICS feed is too large"); output.write(buffer,0,read); }
                return new String(output.toByteArray(),StandardCharsets.UTF_8);
            }
        } finally { if(connection!=null)connection.disconnect(); }
    }
    public static boolean hasFeeds(Context c) { return !new LocalStore(c).icsFeeds().isEmpty(); }
    public static void schedule(Context c) {
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(scheduler==null)return;
        if(!hasFeeds(c)) { scheduler.cancel(JOB_ID); return; }
        JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(c,IcsSyncJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(PERIOD_MILLIS).setPersisted(true).build();
        scheduler.schedule(job);
    }
    private IcsSync() {}
}
