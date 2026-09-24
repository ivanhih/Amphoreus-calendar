package com.amphoreus.calendar.data;

import android.app.job.*;
import android.content.*;
import com.amphoreus.calendar.CalendarApp;
import com.amphoreus.calendar.reminder.Reminders;
import com.amphoreus.calendar.widget.Widgets;

/** Periodic network-bound refresh for user-configured ICS feeds. */
public final class IcsSyncJob extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        CalendarApp.IO.execute(()->{ try { IcsSync.syncAll(this); Reminders.reschedule(this); Widgets.updateAll(this); } finally { jobFinished(params,false); IcsSync.schedule(this); } });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
