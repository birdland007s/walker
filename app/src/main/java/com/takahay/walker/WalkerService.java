package com.takahay.walker;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.icu.util.TimeUnit;
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
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.ConnectionResult;

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
import java.time.Duration;
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

    private static final long LOCATION_STACK_NUMBER = 5;

    private static final String BASEURL = "https://ancient-dawn-23054.herokuapp.com/";

    /**
     * 1 degree nearly equal 1.11 * 1e+5 meter.
     * That is, 10 m nearly equal 9e-5 degree.
     */
    private static final double LOCATION_TOLERANCE = 9e-5;

    /**
     *  set ACCURACY_DECIMAL_POINT = 2, then 123.43564 -> 123.44
     */
    private static final int ACCURACY_DECIMAL_POINT = 2;
    private static final int DURATION_DECIMAL_POINT = 2;

    /**
     * Stores the types of location services the client is interested in using. Used for checking
     * settings to determine if the device has optimal location settings.
     */
    private LocationSettingsRequest mLocationSettingsRequest;

    /*
    *   Structure for Stock Data
    * */
    private class LocationData {
        public double longitude;
        public double latitude;
        public double accuracy;
        public Date time;
        // Time duration in minute.
        public double duration;
    }
    private ArrayList<LocationData> LocationDataArray = new ArrayList<>();

    /**
     *
     * @param v         It is a value which is be rounded off
     * @param point     It is a decimal point, that is if this number is 2, then 1234.34543 becomes 1234.35.
     * @return
     */
    private double RoundOffDouble( double v, int point )
    {
        return Math.round( v * Math.pow(10, (double)point ))
                / Math.pow(10, (double)point );
    }

    private LocationCallBack callback = new LocationCallBack() {
        @Override
        public void stackLocation(Location location) {
            if (location != null) {

                Date current = new Date();
                int stackSize = LocationDataArray.size();
                if( stackSize > 0 ) {

                    // get latest Location value and update its duration value.
                    LocationData last = LocationDataArray.get(stackSize-1);


                    double d = (double)( current.getTime() - last.time.getTime())
                            / (double)java.util.concurrent.TimeUnit.MINUTES.toMillis(1);
                    last.duration = RoundOffDouble( d, DURATION_DECIMAL_POINT );

                    if( Math.abs( location.getLatitude() - last.latitude ) < LOCATION_TOLERANCE
                        && Math.abs( location.getLongitude() - last.longitude ) < LOCATION_TOLERANCE ) {

                        Log.i(TAG, String.format("Location is constant, updated time duration of the last entity to %f", last.duration ) );
                        return;
                    }
                }

                LocationData lct = new LocationData();
                lct.latitude = location.getLatitude();
                lct.longitude = location.getLongitude();
                lct.accuracy = RoundOffDouble( location.getAccuracy(), ACCURACY_DECIMAL_POINT );
//                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
//                lct.time = sdf.format(new Date());
                lct.time = new Date();
                lct.duration = 0.0;
                LocationDataArray.add(lct);
            }

            Log.i(TAG, String.format("LocationDataArrayCount=%d", LocationDataArray.size()) );
            if( LocationDataArray.size() > LOCATION_STACK_NUMBER - 1 ) {
                new HttpResponsAsync().execute("api/entries/");
            }
        }
    };

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

        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.i(TAG, "Google Play Services are not available.code=" + Integer.toString(resultCode));

            com.takahay.walker.Location walkerLocation =
                    new com.takahay.walker.Location( getApplicationContext(), callback );
            walkerLocation.startLocationUpdates();

        }
        else
        {
            Log.i(TAG, "Google Play Services are available.code=" + Integer.toString(resultCode));

            com.takahay.walker.googleLocation googleLocation =
                    new com.takahay.walker.googleLocation( getApplicationContext(), callback );

            Log.i(TAG, "finish create googleLocation");

            googleLocation.createRequest();

            Log.i(TAG, "finish create googleLocation request");
        }

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

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

                //set Location data to Json and clear.
                JSONArray jary = new JSONArray();
                for (LocationData d : LocationDataArray) {
                    JSONObject jobj = new JSONObject();
                    jobj.put("longitude", d.longitude);
                    jobj.put("latitude", d.latitude);
                    jobj.put("accuracy", d.accuracy);
                    jobj.put("time", sdf.format(d.time));
                    jobj.put("duration", d.duration);
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
