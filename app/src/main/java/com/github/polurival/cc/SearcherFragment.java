package com.github.polurival.cc;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;

import com.github.polurival.cc.adapter.AutoCompleteTVAdapter;
import com.github.polurival.cc.model.db.DBHelper;
import com.github.polurival.cc.util.Constants;
import com.github.polurival.cc.util.Logger;

/**
 * Created by Polurival
 * on 14.07.2016.
 *
 * <p>See <a href="http://www.vogella.com/tutorials/AndroidFragments/article.html">Source</a></p>
 */
public class SearcherFragment extends Fragment {

    private Cursor searchCursor;
    private Cursor cursor;
    private SearcherFragment.Listener listener;
    private Spinner fromSpinner;
    private Spinner toSpinner;

    private AutoCompleteTextView currencySearcher;

    private String rateUpdaterClassName;

    public SearcherFragment() {
    }

    /*public void setCursor(Cursor cursor) {
        this.cursor = cursor;
    }*/

    public void setFromSpinner(Spinner fromSpinner) {
        this.fromSpinner = fromSpinner;
    }

    public void setToSpinner(Spinner toSpinner) {
        this.toSpinner = toSpinner;
    }

    public interface Listener {
        Cursor getCursor();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Logger.logD(Logger.getTag(), "onAttach");

        if (context instanceof SearcherFragment.Listener) {
            listener = (SearcherFragment.Listener) context;
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Logger.logD(Logger.getTag(), "onAttach");

        if (activity instanceof SearcherFragment.Listener) {
            listener = (SearcherFragment.Listener) activity;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Logger.logD(Logger.getTag(), "onCreateView()");

        View fragmentView = inflater.inflate(R.layout.fragment_searcher_layout, container, false);
        currencySearcher = (AutoCompleteTextView) fragmentView.findViewById(R.id.tv_auto_complete);

        return fragmentView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Logger.logD(Logger.getTag(), "onActivityCreated");

        Bundle bundle = getArguments();
        if (bundle != null) {
            rateUpdaterClassName = bundle.getString(Constants.RATE_UPDATER_CLASS_NAME);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Logger.logD(Logger.getTag(), "onStart");

        initCurrencySearcher();
    }

    private void initCurrencySearcher() {
        Logger.logD(Logger.getTag(), "initSearchAdapter");

        searchCursor = DBHelper.getSearchCursor("", rateUpdaterClassName);
        AutoCompleteTVAdapter autoCompleteTvAdapter = new AutoCompleteTVAdapter(
                AppContext.getContext(), searchCursor, rateUpdaterClassName);

        currencySearcher.setAdapter(autoCompleteTvAdapter);
        currencySearcher.setThreshold(1);
        currencySearcher.setOnItemClickListener(searcherClickListener);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (null != searchCursor) searchCursor.close();
    }

    private final AdapterView.OnItemClickListener searcherClickListener
            = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Logger.logD(Logger.getTag(), "currencySearcher.onItemClick");

            currencySearcher.setText("");

            Cursor searchedCurrency = (Cursor) parent.getItemAtPosition(position);
            String searchedCharCode = searchedCurrency.getString(1);

            cursor = listener.getCursor();
            int searchedCharCodeSpinnerPos = 0;
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                String cursorCurrentCharCode = cursor.getString(1);
                if (searchedCharCode.equals(cursorCurrentCharCode)) {
                    searchedCharCodeSpinnerPos = cursor.getPosition();
                }
            }

            SpinnerSelectionDialog fragmentDialog = new SpinnerSelectionDialog();
            fragmentDialog.setFromSpinner(fromSpinner);
            fragmentDialog.setToSpinner(toSpinner);
            fragmentDialog.setSearchedCharCodeSpinnerPos(searchedCharCodeSpinnerPos);
            fragmentDialog.show(getFragmentManager(), "list selection");
        }
    };
}
