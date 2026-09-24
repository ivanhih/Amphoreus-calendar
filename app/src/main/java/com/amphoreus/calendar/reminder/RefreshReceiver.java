package com.amphoreus.calendar.reminder;

import android.content.*;
import com.amphoreus.calendar.CalendarApp;
import com.amphoreus.calendar.data.IcsSync;
import com.amphoreus.calendar.widget.*;

public final class RefreshReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        PendingResult pending=goAsync(); CalendarApp.IO.execute(()->{ try { Reminders.reschedule(c); Widgets.updateAll(c); CalendarRefreshJob.schedule(c); IcsSync.schedule(c); } finally { pending.finish(); } });
    }
}
