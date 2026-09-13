package com.amphoreus.calendar.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import java.util.Calendar;
import java.util.Locale;

/** Shared language and appearance preferences used by activities and widgets. */
public final class AppSettings {
    public static final String PREFS_NAME="settings";
    public static final String LANGUAGE_KEY="language";
    public static final String LANGUAGE_SYSTEM="system";
    public static final String LANGUAGE_ZH_CN="zh-CN";
    public static final String LANGUAGE_ZH_TW="zh-TW";
    public static final String LANGUAGE_EN="en";
    public static final String THEME_MODE_KEY="themeMode";
    public static final String THEME_SYSTEM="system";
    public static final String THEME_LIGHT="light";
    public static final String THEME_DARK="dark";
    public static final String AUTO_THEME_KEY="autoTheme";

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);
    }

    public static String language(Context context) {
        return preferences(context).getString(LANGUAGE_KEY,LANGUAGE_SYSTEM);
    }

    public static Locale locale(Context context) {
        String value=language(context);
        if(LANGUAGE_ZH_CN.equals(value))return Locale.SIMPLIFIED_CHINESE;
        if(LANGUAGE_ZH_TW.equals(value))return Locale.TRADITIONAL_CHINESE;
        if(LANGUAGE_EN.equals(value))return Locale.ENGLISH;
        // Read the real device locale, not the activity configuration that may still contain
        // the previous in-app language after the user switches back to "Follow system".
        Configuration configuration=Resources.getSystem().getConfiguration();
        if(Build.VERSION.SDK_INT>=24 && configuration.getLocales()!=null && !configuration.getLocales().isEmpty()) {
            return configuration.getLocales().get(0);
        }
        return configuration.locale==null?Locale.getDefault():configuration.locale;
    }

    /** Apply the selected locale to this context before an activity rebuilds its view tree. */
    public static void applyLocale(Context context) {
        Locale selected=locale(context);
        Locale.setDefault(selected);
        Configuration configuration=new Configuration(context.getResources().getConfiguration());
        if(Build.VERSION.SDK_INT>=24) configuration.setLocale(selected);
        else configuration.locale=selected;
        context.getResources().updateConfiguration(configuration,context.getResources().getDisplayMetrics());
    }

    public static boolean autoTheme(Context context) {
        return preferences(context).getBoolean(AUTO_THEME_KEY,false);
    }

    public static String themeMode(Context context) {
        return preferences(context).getString(THEME_MODE_KEY,THEME_SYSTEM);
    }

    public static boolean isLight(Context context) {
        SharedPreferences preferences=preferences(context);
        if(preferences.getBoolean(AUTO_THEME_KEY,false)) {
            int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            return hour>=7 && hour<19;
        }
        String mode=preferences.getString(THEME_MODE_KEY,THEME_SYSTEM);
        if(THEME_LIGHT.equals(mode))return true;
        if(THEME_DARK.equals(mode))return false;
        int night=context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK;
        return night!=Configuration.UI_MODE_NIGHT_YES;
    }

    private AppSettings() {}
}
