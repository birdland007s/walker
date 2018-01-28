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

    /**
     *
     * That is, 10 m nearly equal 9e-5 degree.
     */
    private static final double LOCATION_DISTANCE_TOLERANCE_METER = 15.0;

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
        public double distance;
    }

    private ArrayList<LocationData> LocationDataArray = new ArrayList<>();
    private LocationData mLastLocationData = null;

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

    /**
     * Calculate distance between two points in latitude and longitude taking
     * into account height difference. If you are not interested in height
     * difference pass 0.0. Uses Haversine method as its base.
     *
     * lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters
     * el2 End altitude in meters
     * @returns Distance in Meters
     */
    public static double getDistance(double lat1, double lon1, double el1,
                                  double lat2, double lon2, double el2 ){

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }

    private LocationCallBack callback = new LocationCallBack() {
        @Override
        public void stackLocation(Location location) {
            if (location != null) {

                Date current = new Date();
                int stackSize = LocationDataArray.size();
                if( mLastLocationData != null ) {

                    double dist = getDistance(
                            mLastLocationData.latitude,
                            mLastLocationData.longitude,
                            0.0,
                            location.getLatitude(),
                            location.getLongitude(),
                            0.0);

                    // check distances between prev and current locations.
                    if( dist < LOCATION_DISTANCE_TOLERANCE_METER ) {

                        Log.i(TAG, String.format("Location is constant. Distance = %f", dist));
                        return;
                    }

                    // update the previous location's time duration.
                    double d = (double)( current.getTime() - mLastLocationData.time.getTime())
                            / (double)java.util.concurrent.TimeUnit.MINUTES.toMillis(1);
                    mLastLocationData.duration = RoundOffDouble( d, DURATION_DECIMAL_POINT );

                    // calculate a distance between prev and current locations.
                    mLastLocationData.distance = dist;

                    // stack last location data.
                    LocationDataArray.add(mLastLocationData);
                }
            }

            //Post locations to the web server, if the stack number is even to the limit.
            Log.i(TAG, String.format("LocationDataArrayCount=%d", LocationDataArray.size()) );
            if( LocationDataArray.size() > LOCATION_STACK_NUMBER - 1 ) {

                try {
                    //set Location data to Json and clear.
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    JSONArray jary = new JSONArray();
                    for (LocationData d : LocationDataArray) {
                        JSONObject jobj = new JSONObject();
                        jobj.put("longitude", d.longitude);
                        jobj.put("latitude", d.latitude);
                        jobj.put("accuracy", d.accuracy);
                        jobj.put("time", sdf.format(d.time));
                        jobj.put("duration", d.duration);
                        jobj.put("distance", d.distance);
                        jobj.put("host", 1);
                        jary.put(jobj);

                        Log.i(TAG, String.format("  BODY/n  %s", jary.toString()));
                        new HttpResponsAsync().execute("api/entries/", jary.toString());
                    }
                    LocationDataArray.clear();
                }
                catch (JSONException error) {
                    Log.i(TAG, error.toString());
                }

            }

            //Stack the current location. The duration should be updated when the next location is given.
            if (location != null) {
                mLastLocationData = new LocationData();
                mLastLocationData.latitude = location.getLatitude();
                mLastLocationData.longitude = location.getLongitude();
                mLastLocationData.accuracy = RoundOffDouble( location.getAccuracy(), ACCURACY_DECIMAL_POINT );
//                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
//                lct.time = sdf.format(new Date());
                mLastLocationData.time = new Date();
                mLastLocationData.duration = 0.0;
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
        int status = 0;

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

        new HttpResponsHelper().postStatusCode( 1, 1 );

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
        new HttpResponsHelper().postStatusCode( 2, 1 );
    }

}
