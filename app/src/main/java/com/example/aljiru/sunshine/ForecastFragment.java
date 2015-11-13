package com.example.aljiru.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class ForecastFragment extends Fragment {

    private static String LOG_NAME = ForecastFragment.class.getSimpleName();
    private ArrayAdapter<String> forecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        ListView listView = (ListView) rootView.findViewById(R.id.listViewForecast);
        forecastAdapter = new ArrayAdapter<>(getActivity(), R.layout.list_item_forecast,
                R.id.list_item_forecast_textview, new ArrayList<String>());
        listView.setAdapter(forecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Intent details = new Intent(getActivity(), DetailActivity.class);
                details.putExtra(Intent.EXTRA_TEXT, forecastAdapter.getItem(pos));
                startActivity(details);
            }
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_refresh:
                updateWeather();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateWeather() {
        SharedPreferences preferences = getDefaultSharedPreferences(getActivity());
        String location = preferences.getString(getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        String units = preferences.getString(getString(R.string.pref_units_key),
                getString(R.string.pref_units_default));
        new FetchWeatherTask().execute(location, units);
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        private static final String TYPE_PARAM = "zip";
        private static final String MODE_PARAM = "mode";
        private static final String UNITS_PARAM = "units";
        private static final String DAYS_PARAM = "cnt";
        private static final String KEY_PARAM = "appid";

        @Override
        protected String[] doInBackground(String... params) {
            String uri = new Uri.Builder()
                    .scheme("http")
                    .authority("api.openweathermap.org")
                    .path("data/2.5/forecast/daily")
                    .appendQueryParameter(TYPE_PARAM, params[0])
                    .appendQueryParameter(MODE_PARAM, "json")
                    .appendQueryParameter(UNITS_PARAM, params[1])
                    .appendQueryParameter(DAYS_PARAM, "7")
                    .appendQueryParameter(KEY_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                    .build().toString();
            Log.i(LOG_NAME, uri);

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(uri).build();

            String result;
            try {
                Response response = client.newCall(request).execute();
                result = response.body().string();
                return getWeatherDataFromJson(result, 7);
            } catch (IOException e) {
                Log.e(LOG_NAME, "Transfer error" + e);
                return null;
            } catch (JSONException e) {
                Log.e(LOG_NAME, "Parsing error" + e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String[] data) {
            if (data == null) return;

            forecastAdapter.clear();
            for (String item : data) {
                forecastAdapter.add(item);
            }
        }

        /**
         * The date/time conversion code is going to be moved outside the asynctask later, so for
         * convenience we're breaking it out into its own method now.
         */
        private String getReadableDateString(long time) {
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);
            return roundedHigh + "/" + roundedLow;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         * <p/>
         * Fortunately parsing is easy: constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.
            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.
            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();
            String[] resultStrs = new String[numDays];
            for (int i = 0; i < weatherArray.length(); i++) {

                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long. We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime;

                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp". Try not to name variables
                // "temp" when working with temperature. It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);
                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }
            return resultStrs;
        }
    }

}
