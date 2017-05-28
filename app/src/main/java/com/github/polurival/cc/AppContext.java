package com.github.polurival.cc;

import android.app.Application;
import android.content.Context;

import net.danlew.android.joda.JodaTimeAndroid;

import java.net.CookieHandler;
import java.net.CookieManager;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

public class AppContext extends Application {

    private static Context appContext;
    private static boolean isActivityVisible; // TODO: 25.05.2017 выпилить это после применения Loader вместо AsyncTask

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;

        maintainUserSession();

        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                        .setDefaultFontPath("font/Roboto-Regular.ttf")
                        .setFontAttrId(R.attr.fontPath)
                        .build()
        );

        JodaTimeAndroid.init(this);
    }

    private void maintainUserSession() {
        //http://stackoverflow.com/a/11036882/5349748 - for avoid java.net.ProtocolException: Server redirected too many times Error
        CookieHandler.setDefault(new CookieManager(null, null));
    }

    public static Context getContext() {
        return appContext;
    }

    public static boolean isActivityVisible() {
        return isActivityVisible;
    }

    public static void activityResumed() {
        isActivityVisible = true;
    }

    public static void activityPaused() {
        isActivityVisible = false;
    }
}
