package com.amphoreus.calendar.widget;

import android.app.job.*;
import android.content.*;
import android.provider.CalendarContract;
import com.amphoreus.calendar.CalendarApp;

public final class CalendarRefreshJob extends JobService {
    private static final int JOB_ID=202609;
    public static void schedule(Context c) {
        if(c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)!=android.content.pm.PackageManager.PERMISSION_GRANTED) { c.getSystemService(JobScheduler.class).cancel(JOB_ID); return; }
        JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(c,CalendarRefreshJob.class))
            .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI,JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(1000).setTriggerContentMaxDelay(10000).build();
        c.getSystemService(JobScheduler.class).schedule(job);
    }
    @Override public boolean onStartJob(JobParameters params) { CalendarApp.IO.execute(()->{ try { Widgets.updateAll(this); } finally { jobFinished(params,false); schedule(this); } }); return true; }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
