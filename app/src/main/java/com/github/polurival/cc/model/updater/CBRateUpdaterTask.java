package com.github.polurival.cc.model.updater;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.github.polurival.cc.AppContext;
import com.github.polurival.cc.R;
import com.github.polurival.cc.RateUpdaterListener;
import com.github.polurival.cc.model.CharCode;
import com.github.polurival.cc.model.Currency;
import com.github.polurival.cc.model.db.DBUpdaterTask;
import com.github.polurival.cc.util.Constants;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.EnumMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Created by Polurival
 * on 26.03.2016.
 */
public class CBRateUpdaterTask extends AsyncTask<Void, Void, Boolean> implements RateUpdater {

    private Context appContext;
    private RateUpdaterListener rateUpdaterListener;
    private EnumMap<CharCode, Currency> currencyMap;

    public CBRateUpdaterTask() {
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
            URL url = new URL(Constants.CBR_URL);
            URLConnection connection = url.openConnection();

            Document doc = parseXML(connection.getInputStream());

            fillCurrencyMapFromSource(doc);
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
            rateUpdaterListener.setMenuState(null);
        }
    }

    @Override
    public <T> void fillCurrencyMapFromSource(T doc) {
        NodeList descNodes = ((Document) doc).getElementsByTagName(Constants.CURRENCY_NODE_LIST);

        for (int i = 0; i < descNodes.getLength(); i++) {
            NodeList currencyNodeList = descNodes.item(i).getChildNodes();
            CharCode charCode = null;
            String nominal = null;
            String rate = null;

            for (int j = 0; j < currencyNodeList.getLength(); j++) {
                String nodeName = currencyNodeList.item(j).getNodeName();
                String textContent = currencyNodeList.item(j).getTextContent();

                if (Constants.CHAR_CODE_NODE.equals(nodeName)) {
                    charCode = CharCode.valueOf(textContent);
                } else if (Constants.NOMINAL_NODE.equals(nodeName)) {
                    nominal = textContent;
                } else if (Constants.RATE_NODE.equals(nodeName)) {
                    rate = textContent.replace(',', '.');
                }

                if (null != charCode && null != nominal && null != rate) {
                    currencyMap.put(charCode,
                            new Currency(Integer.valueOf(nominal), Double.valueOf(rate)));
                    break;
                }
            }
        }
    }

    private Document parseXML(InputStream stream) throws Exception {
        DocumentBuilderFactory objDocumentBuilderFactory;
        DocumentBuilder objDocumentBuilder;
        Document doc;

        objDocumentBuilderFactory = DocumentBuilderFactory.newInstance();
        objDocumentBuilder = objDocumentBuilderFactory.newDocumentBuilder();
        doc = objDocumentBuilder.parse(stream);

        return doc;
    }

    @Override
    public String getDescription() {
        return appContext.getString(R.string.cb_rf);
    }
}