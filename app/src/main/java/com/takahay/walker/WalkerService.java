package com.takahay.walker;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Created by takahay on 2018/01/22.
 */

public class WalkerService extends Service {
    private static final String TAG = "WalkerService";

    /**
     * Constant used in the location settings dialog.
     */
    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 30000;

    private static final float LOCATION_DISTANCE = 10f;

    private static final long LOCATION_STACK_NUMBER = 5;

    private static final String BASEURL = "https://ancient-dawn-23054.herokuapp.com/";

    private LocationManager mLocationManager = null;


    /**
     * Stores the types of location services the client is interested in using. Used for checking
     * settings to determine if the device has optimal location settings.
     */
    private LocationSettingsRequest mLocationSettingsRequest;

    /**
     * Callback for Location events.
     */
    private LocationCallback mLocationCallback;


    /**
     *   Service
     */

    @Override
    public IBinder onBind(Intent arg0)
    {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);

        startLocationUpdates();

        return START_STICKY;
    }

    @Override
    public void onCreate()
    {
        Log.i(TAG, "onCreate");

    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy");

    }

    /*
    *   Structure for Stock Data
    * */
    private class LocationData {
        public double longitude;
        public double latitude;
        public String time;
    }
    private ArrayList<LocationData> LocationDataArray = new ArrayList<>();

    private class LocationListener implements android.location.LocationListener{
        Location mLastLocation;
        public LocationListener(String provider)
        {
            Log.i(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }
        @Override
        public void onLocationChanged(Location location)
        {
            Log.i(TAG, "onLocationChanged: " + location);
            mLastLocation.set(location);

            stackLocation(location);
        }
        @Override
        public void onProviderDisabled(String provider)
        {
            Log.i(TAG, "onProviderDisabled: " + provider);
        }
        @Override
        public void onProviderEnabled(String provider)
        {
            Log.i(TAG, "onProviderEnabled: " + provider);
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras)
        {
            Log.i(TAG, "onStatusChanged: " + provider);
        }
    }
    LocationListener[] mLocationListeners = new LocationListener[] {
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private void startLocationUpdates() {
        Log.e(TAG, "onCreate");
        initializeLocationManager();
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, UPDATE_INTERVAL_IN_MILLISECONDS, LOCATION_DISTANCE,
                    mLocationListeners[1]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_IN_MILLISECONDS, LOCATION_DISTANCE,
                    mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }
    }

    private void initializeLocationManager() {
        Log.i(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    private void stackLocation(Location location)
    {
        if (location != null) {

            LocationData lct = new LocationData();
            lct.latitude = location.getLatitude();
            lct.longitude = location.getLongitude();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            lct.time = sdf.format(new Date());
            LocationDataArray.add(lct);
        }

        Log.i(TAG, String.format("LocationDataArrayCount=%d", LocationDataArray.size()) );
        if( LocationDataArray.size() > LOCATION_STACK_NUMBER - 1 ) {
            new HttpResponsAsync().execute("api/entries/");
        }
    }

    /**
     *    Provide a REST Post function.
     */

    class HttpResponsAsync extends android.os.AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // doInBackground前処理
        }

        @Override
        protected String doInBackground(String... function) {

            HttpURLConnection httpCon = null;
            StringBuffer result = new StringBuffer();
            try {
                Log.i(TAG, String.format("Post Location Data to Web. [%s%s]", BASEURL, function[0]));
                URL url = new URL(BASEURL + function[0]);

                httpCon = (HttpURLConnection) url.openConnection();
                httpCon.setDoOutput(true);
                httpCon.setDoInput(true);
                httpCon.setUseCaches(false);
                httpCon.setRequestProperty("Content-Type", "application/json");
                httpCon.setRequestProperty("Accept", "application/json");
                httpCon.setRequestMethod("POST");

                OutputStream os = httpCon.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");

                //set Location data to Json and clear.
                JSONArray jary = new JSONArray();
                for (LocationData d : LocationDataArray) {
                    JSONObject jobj = new JSONObject();
                    jobj.put("longitude", d.longitude);
                    jobj.put("latitude", d.latitude);
                    jobj.put("time", d.time);
                    jobj.put("host", 1);
                    jary.put(jobj);
                }
                LocationDataArray.clear();

                //JSONObject jdt = new JSONObject();
                //jdt.put("data",jary);


                Log.i(TAG, String.format("  BODY/n  %s", jary.toString()));
                osw.write(jary.toString());
                osw.flush();
                osw.close();
                //os.close();
                httpCon.connect();

                // HTTPレスポンスコード
                final int status = httpCon.getResponseCode();
                if (status == HttpURLConnection.HTTP_CREATED) {
                    // 通信に成功した
                    // テキストを取得する
                    final InputStream in = httpCon.getInputStream();
                    String encoding = httpCon.getContentEncoding();
                    if (null == encoding) {
                        encoding = "UTF-8";
                    }
                    final InputStreamReader inReader = new InputStreamReader(in, encoding);
                    final BufferedReader bufReader = new BufferedReader(inReader);
                    String line = null;
                    // 1行ずつテキストを読み込む
                    while ((line = bufReader.readLine()) != null) {
                        result.append(line);
                    }
                    bufReader.close();
                    inReader.close();
                    in.close();
                } else {
                    Log.i(TAG, String.format("HttpURLConnection response:  %s", status));
                }
            } catch (MalformedURLException error) {
                //Handles an incorrectly entered URL
                Log.i(TAG, error.toString());
            } catch (SocketTimeoutException error) {
//Handles URL access timeout.
                Log.i(TAG, error.toString());

            } catch (IOException error) {
//Handles input and output errors
                Log.i(TAG, error.toString());

            } catch (JSONException error) {
                Log.i(TAG, error.toString());
            } finally {
                if (httpCon != null) httpCon.disconnect();
            }

            Log.i(TAG, String.format("HttpURLConnection result:  %s", result.toString()));
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            // doInBackground後処理
        }
    }
}
