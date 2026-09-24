package com.amphoreus.calendar;

import android.app.Application;
import android.Manifest;
import android.database.ContentObserver;
import android.os.*;
import com.amphoreus.calendar.data.EventRepository;
import com.amphoreus.calendar.data.IcsSync;
import com.amphoreus.calendar.widget.*;
import com.amphoreus.calendar.reminder.Reminders;
import com.amphoreus.calendar.ui.Ui;
import java.util.concurrent.*;

public final class CalendarApp extends Application {
    public static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private EventRepository repository;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean calendarObserverRegistered;
    private final ContentObserver calendarObserver=new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { handler.removeCallbacks(refresh); handler.postDelayed(refresh,1000); }
    };
    public EventRepository repository() { return repository; }
    @Override public void onCreate() {
        super.onCreate(); Ui.applyTheme(this); repository=new EventRepository(this);
        Reminders.createChannel(this);
        refreshCalendarObserver();
        IcsSync.schedule(this);
        IO.execute(()->{ Reminders.reschedule(this); Widgets.updateAll(this); CalendarRefreshJob.schedule(this); IcsSync.schedule(this); });
    }
    private final Runnable refresh=()->IO.execute(()->{ Reminders.reschedule(this); Widgets.updateAll(this); });
    public void refreshCalendarObserver() {
        boolean allowed=repository!=null && repository.system.canObserve();
        if(!allowed) {
            if(calendarObserverRegistered) { try { getContentResolver().unregisterContentObserver(calendarObserver); } catch(Exception ignored) {} calendarObserverRegistered=false; }
            return;
        }
        if(calendarObserverRegistered)return;
        try { getContentResolver().registerContentObserver(android.provider.CalendarContract.CONTENT_URI,true,calendarObserver); calendarObserverRegistered=true; }
        catch(SecurityException ignored) { calendarObserverRegistered=false; }
    }
    public void changed() { refreshCalendarObserver(); IcsSync.schedule(this); IO.execute(()->{ Reminders.reschedule(this); Widgets.updateAll(this); CalendarRefreshJob.schedule(this); IcsSync.schedule(this); }); }
}
