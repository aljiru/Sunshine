package com.example.aljiru.sunshine;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.ShareActionProvider;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;

import static android.support.v4.view.MenuItemCompat.getActionProvider;

/**
 * A placeholder fragment containing a simple view.
 */
public class DetailFragment extends Fragment {

    private static final String FORECAST_SHARE_HASHTAG = " #SunshineApp";

    @Bind(R.id.textView)
    TextView textView;

    private String forecastMessage;

    public DetailFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.detailfragment, menu);

        MenuItem item = menu.findItem(R.id.menu_item_share);
        ShareActionProvider shareActionProvider = (ShareActionProvider) getActionProvider(item);

        if (shareActionProvider != null) {
            shareActionProvider.setShareIntent(createShareIntent());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_item_share:
                createShareIntent();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private Intent createShareIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, forecastMessage + FORECAST_SHARE_HASHTAG);
        return intent;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_detail, container, false);
        ButterKnife.bind(this, view);
        Intent intent = getActivity().getIntent();
        if (intent != null && intent.hasExtra(Intent.EXTRA_TEXT)) {
            forecastMessage = intent.getStringExtra(Intent.EXTRA_TEXT);
            textView.setText(forecastMessage);
        }
        return view;
    }
}
