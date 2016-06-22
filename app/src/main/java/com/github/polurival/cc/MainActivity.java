package com.github.polurival.cc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ShareActionProvider;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.polurival.cc.adapter.SpinnerCursorAdapter;
import com.github.polurival.cc.model.updater.CBRateUpdaterTask;
import com.github.polurival.cc.model.updater.CustomRateUpdaterMock;
import com.github.polurival.cc.model.TaskCanceler;
import com.github.polurival.cc.model.updater.YahooRateUpdaterTask;
import com.github.polurival.cc.model.db.DBHelper;
import com.github.polurival.cc.model.db.DBReaderTask;
import com.github.polurival.cc.model.updater.RateUpdater;
import com.github.polurival.cc.model.db.OnBackPressedListener;
import com.github.polurival.cc.util.Constants;
import com.github.polurival.cc.util.DateUtil;
import com.github.polurival.cc.util.Logger;

import org.joda.time.LocalDateTime;

import java.lang.reflect.Method;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;

/**
 * Created by Polurival
 * on 24.03.2016.
 */
public class MainActivity extends Activity implements RateUpdaterListener, OnRefreshListener {

    private SQLiteDatabase db;
    private Cursor cursor;
    private Cursor fromCursor;
    private Cursor toCursor;

    private SharedPreferences preferences;

    private ShareActionProvider shareActionProvider;

    private String menuState;
    private OnBackPressedListener onBackPressedListener;

    private Handler taskCancelerHandler;
    private TaskCanceler taskCanceler;

    private RateUpdater rateUpdater;
    private LocalDateTime upDateTime;

    private PullToRefreshLayout mPullToRefreshLayout;

    private EditText editFromAmount;
    private EditText editToAmount;

    private boolean isPropertiesLoaded;
    private boolean isNeedToReSwapValues;
    private boolean ignoreEditFromAmountChange;
    private boolean ignoreEditToAmountChange;

    private Spinner fromSpinner;
    private int fromSpinnerSelectedPos;
    private String currencyFromCharCode;
    double currencyFromNominal;
    double currencyFromToXRate;

    private Spinner toSpinner;
    private int toSpinnerSelectedPos;
    private String currencyToCharCode;
    double currencyToNominal;
    double currencyToToXRate;

    private TextView tvDateTime;

    @Override
    public void setOnBackPressedListener(OnBackPressedListener onBackPressedListener) {
        this.onBackPressedListener = onBackPressedListener;
    }

    @Override
    public void setMenuState(String menuState) {
        this.menuState = menuState;
        invalidateOptionsMenu();
    }

    @Override
    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public void setUpDateTime(LocalDateTime upDateTime) {
        this.upDateTime = upDateTime;
    }

    @Override
    public void setPropertiesLoaded(boolean isLoaded) {
        this.isPropertiesLoaded = isLoaded;
    }

    @Override
    public RateUpdater getRateUpdater() {
        return rateUpdater;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = DBHelper.getInstance(getApplicationContext()).getReadableDatabase();
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        ignoreEditFromAmountChange = false;
        ignoreEditToAmountChange = false;
        isNeedToReSwapValues = false;
        isPropertiesLoaded = false;

        mPullToRefreshLayout = (PullToRefreshLayout) findViewById(R.id.ptr_layout);
        ActionBarPullToRefresh.from(this)
                .allChildrenArePullable()
                .listener(this)
                .setup(mPullToRefreshLayout);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        initEditAmount();

        loadRateUpdaterProperties();
        loadProperties();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (DateUtil.compareUpDateWithCurrentDate(upDateTime)) {
            if (!(rateUpdater instanceof CustomRateUpdaterMock)) {
                mPullToRefreshLayout.setRefreshing(true);
            }
            updateRatesFromSource();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        readDataFromDB();
        checkAsyncTaskStatusAndSetNewInstance();
    }

    @Override
    protected void onPause() {
        cancelAsyncTask();

        super.onPause();
    }

    @Override
    protected void onStop() {
        saveProperties();

        if (null != taskCanceler && null != taskCancelerHandler) {
            taskCancelerHandler.removeCallbacks(taskCanceler);
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (null != fromCursor) fromCursor.close();
        if (null != toCursor) toCursor.close();
        if (null != cursor) cursor.close();

        if (null != db) db.close();

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        cancelAsyncTask();

        super.onBackPressed();
    }

    @Override
    protected void onUserLeaveHint() {
        cancelAsyncTask();

        super.onUserLeaveHint();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        if (Constants.MENU_HIDE.equals(menuState)) {
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setVisible(false);
            }
        }

        MenuItem shareAction = menu.findItem(R.id.share_action);
        shareActionProvider = (ShareActionProvider) shareAction.getActionProvider();

        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Show menu icons
     * See <a href="http://stackoverflow.com/a/22668665/5349748">source</a>
     */
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_ACTION_BAR && menu != null) {
            if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
                try {
                    Method m = menu.getClass().getDeclaredMethod(
                            "setOptionalIconsVisible", Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(menu, true);
                } catch (Exception e) {
                    Logger.logD("onMenuOpened error");
                    throw new RuntimeException(e);
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.data_source_action:
                cancelAsyncTask();

                Intent dataSourceIntent = new Intent(this, DataSourceActivity.class);
                startActivity(dataSourceIntent);
                return true;

            case R.id.currency_switching_action:
                cancelAsyncTask();

                Intent currencySwitchingIntent = new Intent(this, CurrencySwitchingActivity.class);
                startActivity(currencySwitchingIntent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void checkAsyncTaskStatusAndSetNewInstance() {
        if (rateUpdater instanceof AsyncTask) {
            if (((AsyncTask) rateUpdater).getStatus() != AsyncTask.Status.PENDING) {
                loadRateUpdaterProperties();
            }
        }
    }

    @Override
    public void onRefreshStarted(View view) {
        updateRates();
    }

    @Override
    public void stopRefresh() {
        if (mPullToRefreshLayout.isRefreshing()) {
            mPullToRefreshLayout.setRefreshComplete();
        }
    }

    private void updateRates() {
        if (rateUpdater instanceof CustomRateUpdaterMock) {
            Toast.makeText(this, R.string.custom_updating_info, Toast.LENGTH_SHORT).show();
            stopRefresh();
        } else {
            loadRateUpdaterProperties();

            updateRatesFromSource();

            saveProperties();

            loadSpinnerProperties();
        }
    }

    private void updateRatesFromSource() {
        taskCancelerHandler.postDelayed(taskCanceler, 15 * 1000);
        if (rateUpdater instanceof CBRateUpdaterTask) {
            ((CBRateUpdaterTask) rateUpdater).execute();
        } else if (rateUpdater instanceof YahooRateUpdaterTask) {
            ((YahooRateUpdaterTask) rateUpdater).execute();
        }

        hideMenuWhileUpdating();
    }

    private void hideMenuWhileUpdating() {
        menuState = Constants.MENU_HIDE;
        invalidateOptionsMenu();
    }

    @Override
    public void readDataFromDB() {
        DBReaderTask dbReaderTask = new DBReaderTask();
        setOnBackPressedListener(dbReaderTask);
        dbReaderTask.setRateUpdaterListener(this);

        if (rateUpdater instanceof CBRateUpdaterTask) {
            dbReaderTask.execute(DBHelper.COLUMN_NAME_CB_RF_SOURCE,
                    DBHelper.COLUMN_NAME_CB_RF_NOMINAL,
                    DBHelper.COLUMN_NAME_CB_RF_RATE);
        } else if (rateUpdater instanceof YahooRateUpdaterTask) {
            dbReaderTask.execute(DBHelper.COLUMN_NAME_YAHOO_SOURCE,
                    DBHelper.COLUMN_NAME_YAHOO_NOMINAL,
                    DBHelper.COLUMN_NAME_YAHOO_RATE);
        } else if (rateUpdater instanceof CustomRateUpdaterMock) {
            dbReaderTask.execute(DBHelper.CUSTOM_SOURCE_MOCK,
                    DBHelper.COLUMN_NAME_CUSTOM_NOMINAL,
                    DBHelper.COLUMN_NAME_CUSTOM_RATE);
        }
    }

    @Override
    public void initSpinners() {
        SpinnerCursorAdapter cursorAdapter =
                new SpinnerCursorAdapter(getApplicationContext(), cursor);

        fromSpinner = (Spinner) findViewById(R.id.from_spinner);
        fromSpinner.setAdapter(cursorAdapter);
        fromSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fromCursor = (Cursor) parent.getItemAtPosition(position);

                currencyFromCharCode = fromCursor.getString(1);
                currencyFromNominal = (double) fromCursor.getInt(2);
                currencyFromToXRate = fromCursor.getDouble(3);

                fromSpinnerSelectedPos = position;

                editFromAmount.setText(editFromAmount.getText());

                syncShareActionData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        toSpinner = (Spinner) findViewById(R.id.to_spinner);
        toSpinner.setAdapter(cursorAdapter);
        toSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                toCursor = (Cursor) parent.getItemAtPosition(position);

                currencyToCharCode = toCursor.getString(1);
                currencyToNominal = (double) toCursor.getInt(2);
                currencyToToXRate = toCursor.getDouble(3);

                toSpinnerSelectedPos = position;

                editFromAmount.setText(editFromAmount.getText());

                syncShareActionData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (fromSpinner.getCount() == 0) {
            Toast.makeText(getApplicationContext(),
                    getApplicationContext().getString(R.string.all_currencies_disabled),
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void initEditAmount() {
        editFromAmount = (EditText) findViewById(R.id.edit_from_amount);
        editFromAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() != 0 && isPropertiesLoaded) {
                    if (!ignoreEditFromAmountChange) {
                        ignoreEditToAmountChange = true;
                        convertAndSetResult(editFromAmount);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                ignoreEditToAmountChange = false;
                if ("".equals(s.toString())) {
                    editToAmount.getText().clear();
                }

                syncShareActionData();
            }
        });

        editToAmount = (EditText) findViewById(R.id.edit_to_amount);
        editToAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() != 0 && isPropertiesLoaded) {
                    if (!ignoreEditToAmountChange) {
                        ignoreEditFromAmountChange = true;
                        convertAndSetResult(editToAmount);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                ignoreEditFromAmountChange = false;
                if ("".equals(s.toString())) {
                    editFromAmount.getText().clear();
                }

                syncShareActionData();
            }
        });
    }

    @Override
    public void initTvDateTime() {
        tvDateTime = (TextView) findViewById(R.id.tv_date_time);
        tvDateTime.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                fromSpinner.setSelection(fromSpinner.getSelectedItemPosition());
                toSpinner.setSelection(toSpinner.getSelectedItemPosition());
            }
        });
        tvDateTimeSetText();
    }

    private void tvDateTimeSetText() {
        tvDateTime.setText(String.format("%s%s",
                rateUpdater.getDescription(), DateUtil.getUpDateTimeStr(upDateTime)));
    }

    public void swapFromTo(View v) {
        if (fromSpinner != null && toSpinner != null) {
            int fromSpinnerSelectedItemPos = fromSpinner.getSelectedItemPosition();
            fromSpinner.setSelection(toSpinner.getSelectedItemPosition());
            toSpinner.setSelection(fromSpinnerSelectedItemPos);
        }
    }

    private void convertAndSetResult(View v) {
        if (null == fromSpinner.getSelectedItem() || null == toSpinner.getSelectedItem()) {
            Toast.makeText(getApplicationContext(),
                    getApplicationContext().getString(R.string.all_currencies_disabled),
                    Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        if (isNeedToReSwapValues && (v.getId() != R.id.edit_to_amount)) {
            double tempValFrom = currencyFromToXRate;
            currencyFromToXRate = currencyToToXRate;
            currencyToToXRate = tempValFrom;

            double tempNomFrom = currencyFromNominal;
            currencyFromNominal = currencyToNominal;
            currencyToNominal = tempNomFrom;

            isNeedToReSwapValues = false;
        }

        if (!isNeedToReSwapValues && (v.getId() == R.id.edit_to_amount)) {
            double tempValFrom = currencyFromToXRate;
            currencyFromToXRate = currencyToToXRate;
            currencyToToXRate = tempValFrom;

            double tempNomFrom = currencyFromNominal;
            currencyFromNominal = currencyToNominal;
            currencyToNominal = tempNomFrom;

            isNeedToReSwapValues = true;
        }

        double enteredAmountOfMoney = getEnteredAmountOfMoney(v);

        double result;
        if (rateUpdater instanceof CBRateUpdaterTask) {
            result = enteredAmountOfMoney *
                    (currencyFromToXRate / currencyToToXRate) *
                    (currencyToNominal / currencyFromNominal);
        } else {
            result = enteredAmountOfMoney *
                    (currencyToToXRate / currencyFromToXRate) *
                    (currencyFromNominal / currencyToNominal);
        }


        if (v.getId() == R.id.edit_from_amount) {
            if ("".equals(editFromAmount.getText().toString())) {
                editToAmount.setText("");
            } else {
                editToAmount.setText(String.format("%.2f", result).replace(",", "."));
            }
        } else if (v.getId() == R.id.edit_to_amount) {
            if ("".equals(editToAmount.getText().toString())) {
                editFromAmount.setText("");
            } else {
                editFromAmount.setText(String.format("%.2f", result).replace(",", "."));
            }
        }
    }

    private double getEnteredAmountOfMoney(View v) {
        if (v.getId() == R.id.edit_from_amount) {
            if (TextUtils.isEmpty(editFromAmount.getText().toString())) {
                return 0d;
            }
            return (double) Float.parseFloat(editFromAmount.getText().toString().replace(",", "."));
        } else {
            if (TextUtils.isEmpty(editToAmount.getText().toString())) {
                return 0d;
            }
            return (double) Float.parseFloat(editToAmount.getText().toString().replace(",", "."));
        }
    }

    private void saveProperties() {
        SharedPreferences.Editor editor = preferences.edit();

        if (rateUpdater instanceof CBRateUpdaterTask) {
            editor.putInt(getString(R.string.saved_cb_rf_from_spinner_pos),
                    fromSpinnerSelectedPos);
            editor.putInt(getString(R.string.saved_cb_rf_to_spinner_pos),
                    toSpinnerSelectedPos);
        } else if (rateUpdater instanceof YahooRateUpdaterTask) {
            editor.putInt(getString(R.string.saved_yahoo_from_spinner_pos),
                    fromSpinnerSelectedPos);
            editor.putInt(getString(R.string.saved_yahoo_to_spinner_pos),
                    toSpinnerSelectedPos);
        } else {
            editor.putInt(getString(R.string.saved_custom_from_spinner_pos),
                    fromSpinnerSelectedPos);
            editor.putInt(getString(R.string.saved_custom_to_spinner_pos),
                    toSpinnerSelectedPos);
        }

        editor.putString(getString(R.string.saved_from_edit_amount_text),
                editFromAmount.getText().toString());
        editor.putString(getString(R.string.saved_to_edit_amount_text),
                editToAmount.getText().toString());

        editor.putString(getString(R.string.saved_rate_updater_class),
                rateUpdater.getClass().getName());

        editor.apply();
    }

    @Override
    public void saveDateProperties() {
        SharedPreferences.Editor editor = preferences.edit();

        if (rateUpdater instanceof CBRateUpdaterTask) {
            editor.putLong(getString(R.string.saved_cb_rf_up_date_time),
                    DateUtil.getUpDateTimeInSeconds(upDateTime));
        } else if (rateUpdater instanceof YahooRateUpdaterTask) {
            editor.putLong(getString(R.string.saved_yahoo_up_date_time),
                    DateUtil.getUpDateTimeInSeconds(upDateTime));
        }

        editor.apply();
    }

    private void loadProperties() {
        String editFromAmountText =
                preferences.getString(getString(R.string.saved_from_edit_amount_text),
                        getString(R.string.saved_edit_amount_text_default));
        editFromAmount.setText(editFromAmountText);
        String editToAmountText =
                preferences.getString(getString(R.string.saved_to_edit_amount_text),
                        getString(R.string.saved_edit_amount_text_default));
        editToAmount.setText(editToAmountText);

        String savedUpDateTime;
        if (rateUpdater instanceof CBRateUpdaterTask) {
            savedUpDateTime = getString(R.string.saved_cb_rf_up_date_time);
        } else if (rateUpdater instanceof YahooRateUpdaterTask) {
            savedUpDateTime = getString(R.string.saved_yahoo_up_date_time);
        } else {
            savedUpDateTime = getString(R.string.saved_custom_up_date_time);
        }
        long upDateTimeInSeconds =
                preferences.getLong(savedUpDateTime, DateUtil.getDefaultDateTimeInSeconds());
        upDateTime = DateUtil.getUpDateTime(upDateTimeInSeconds);
    }

    private void loadRateUpdaterProperties() {
        String rateUpdaterName =
                preferences.getString(getString(R.string.saved_rate_updater_class),
                        getString(R.string.saved_rate_updater_class_default));
        Logger.logD("rateUpdater className = " + rateUpdaterName);
        try {
            rateUpdater
                    = (RateUpdater) Class.forName(rateUpdaterName).getConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        rateUpdater.setRateUpdaterListener(this);

        taskCancelerHandler = new Handler();
        taskCanceler = new TaskCanceler((AsyncTask) rateUpdater, this);
    }

    @Override
    public void loadSpinnerProperties() {
        if (rateUpdater instanceof CBRateUpdaterTask) {
            fromSpinnerSelectedPos =
                    preferences.getInt(getString(R.string.saved_cb_rf_from_spinner_pos), 30);
            toSpinnerSelectedPos =
                    preferences.getInt(getString(R.string.saved_cb_rf_to_spinner_pos), 23);
        } else if (rateUpdater instanceof YahooRateUpdaterTask) {
            fromSpinnerSelectedPos =
                    preferences.getInt(getString(R.string.saved_yahoo_from_spinner_pos), 143);
            toSpinnerSelectedPos =
                    preferences.getInt(getString(R.string.saved_yahoo_to_spinner_pos), 116);
        } else {
            fromSpinnerSelectedPos =
                    preferences.getInt(getString(R.string.saved_custom_from_spinner_pos), 143);
            toSpinnerSelectedPos =
                    preferences.getInt(getString(R.string.saved_custom_to_spinner_pos), 116);
        }

        fromSpinner.setSelection(fromSpinnerSelectedPos);
        toSpinner.setSelection(toSpinnerSelectedPos);
    }

    private void syncShareActionData() {
        if (isPropertiesLoaded) {
            setShareIntent(composeText());
        }
    }

    private void setShareIntent(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        shareActionProvider.setShareIntent(intent);
    }

    private String composeText() {
        return String.format("%s %s = %s %s",
                editFromAmount.getText().toString(), currencyFromCharCode,
                editToAmount.getText().toString(), currencyToCharCode);
    }

    private void cancelAsyncTask() {
        stopRefresh();

        AsyncTask task = (AsyncTask) rateUpdater;
        if (task.getStatus() != AsyncTask.Status.PENDING) {
            task.cancel(true);

            if (null != onBackPressedListener) {
                onBackPressedListener.notifyBackPressed();
            }
        }
    }
}
