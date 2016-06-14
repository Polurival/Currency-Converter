package com.github.polurival.cc.model;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.github.polurival.cc.AppContext;
import com.github.polurival.cc.R;
import com.github.polurival.cc.model.db.DBUpdaterTask;
import com.github.polurival.cc.util.Constants;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.EnumMap;

/**
 * Created by Polurival
 * on 11.06.2016.
 */
public class YahooRateUpdaterTask extends AsyncTask<Void, Void, Boolean> implements RateUpdater {

    private Context appContext;
    private RateUpdaterListener rateUpdaterListener;
    private EnumMap<CharCode, Currency> currencyMap;

    public YahooRateUpdaterTask() {
        this.appContext = AppContext.getContext();
    }

    @Override
    public void setRateUpdaterListener(RateUpdaterListener rateUpdaterListener) {
        this.rateUpdaterListener = rateUpdaterListener;
    }

    @Override
    protected void onPreExecute() {
        currencyMap = new EnumMap<>(CharCode.class);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            String jsonStr =
                    IOUtils.toString(new URL(Constants.YAHOO_URL), Charset.forName("UTF-8"));

            if (jsonStr != null) {
                fillCurrencyMapFromSource(jsonStr);
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (result) {
            DBUpdaterTask dbUpdaterTask = new DBUpdaterTask();
            rateUpdaterListener.setOnBackPressedListener(dbUpdaterTask);
            dbUpdaterTask.setRateUpdaterListener(rateUpdaterListener);
            dbUpdaterTask.setCurrencyMap(currencyMap);
            dbUpdaterTask.execute();
        } else {
            Toast.makeText(appContext, appContext.getString(R.string.update_error),
                    Toast.LENGTH_SHORT)
                    .show();
            rateUpdaterListener.stopRefresh();
        }
    }

    @Override
    public <T> void fillCurrencyMapFromSource(T doc) throws JSONException {
        JSONObject yahooAllCurrencies = new JSONObject((String) doc);
        JSONObject list = yahooAllCurrencies.getJSONObject(Constants.LIST_OBJECT);
        JSONArray resources = list.getJSONArray(Constants.RESOURCES_ARRAY);

        for (int i = 0; i < resources.length(); i++) {
            JSONObject resource =
                    resources.getJSONObject(i).getJSONObject(Constants.RESOURCE_OBJECT);
            JSONObject fields = resource.getJSONObject(Constants.FIELDS_OBJECT);

            String code = fields.getString(Constants.SYMBOL_KEY).substring(0, 3);
            double rate = fields.getDouble(Constants.PRICE_KEY);

            CharCode charCode;
            try {
                charCode = CharCode.valueOf(code);
            } catch (IllegalArgumentException e) {
                continue;
            }

            int nominal = 1;
            if (rate < 1) {
                int j = 0;
                while (rate < 1) {
                    rate *= 10;
                    j++;
                }
                nominal = (int) Math.pow(10, j);
            }

            currencyMap.put(charCode, new Currency(nominal, rate));
        }

    }

    @Override
    public String getDescription() {
        return appContext.getString(R.string.yahoo);
    }
}
