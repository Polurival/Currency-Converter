package com.github.polurival.cc;

import android.database.Cursor;

import com.github.polurival.cc.model.updater.RateUpdater;

import org.joda.time.LocalDateTime;

public interface RateUpdaterListener {

    /**
     * For hide menu when updating
     *
     * @param menuState - MENU_HIDE or NULL
     */
    void setMenuState(String menuState);

    /**
     * Set Cursor after reading from database
     */
    void setCommonCursor(Cursor commonCursor);

    /**
     * Start convert after loading all necessary SharedPreferences
     */
    void setPropertiesLoaded(boolean isLoaded);

    /**
     * Set proper Date and Time for respective RateUpdater
     */
    void setUpDateTime(LocalDateTime upDateTime);

    /**
     * Init Spinners after reading from database
     */
    void initSpinners();

    /**
     * Init TextView for Date and Time after reading from database
     */
    void initTvDateTime();

    /**
     * Set selected positions for spinners
     */
    void loadSpinnersProperties();

    /**
     * Save Date and Time in SharedPreferences after successful updating
     */
    void saveUpDateTimeProperty();

    /**
     * Read all necessary data
     */
    void readDataFromDB();

    /**
     * stop refresh animation of PullToRefreshLayout
     */
    void stopRefresh();

    /**
     * For determining which columns of database should be updated
     */
    RateUpdater getRateUpdater();

    /**
     * Set new instance of RateUpdater after cancel old one
     */
    void checkAsyncTaskStatusAndSetNewInstance();

}
