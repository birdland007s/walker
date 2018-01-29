package com.takahay.walker;

import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by takahay on 2018/01/24.
 */

public class Location {

    private static final String TAG = "walker.Location";

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 90000;

    private static final float LOCATION_DISTANCE = 15f;

    private LocationManager mLocationManager = null;

    private LocationCallBack locationCallback;

    private Context mContext;
    /**
     *      Constructor
     */
    public Location(  Context context, LocationCallBack callback)
    {
        mContext = context;
        locationCallback = callback;
    }

    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    public boolean startLocationUpdates() {
        boolean status = false;
        Log.e(TAG, "startLocationUpdates start");
        initializeLocationManager();
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, UPDATE_INTERVAL_IN_MILLISECONDS, LOCATION_DISTANCE,
                    mLocationListeners[1]);
            status = true;
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }

        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_IN_MILLISECONDS, LOCATION_DISTANCE,
                    mLocationListeners[0]);
            status = true;
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }

        new HttpResHelper().postStatusCode( 3, 1 );
        return status;
    }

    private void initializeLocationManager() {
        Log.i(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        }
    }

    private class LocationListener implements android.location.LocationListener{
        android.location.Location mLastLocation;
        public LocationListener(String provider)
        {
            Log.i(TAG, "LocationListener " + provider);
            mLastLocation = new android.location.Location(provider);
        }
        @Override
        public void onLocationChanged(android.location.Location location)
        {
            Log.i(TAG, "onLocationChanged: " + location);
            mLastLocation.set(location);

            locationCallback.stackLocation(location);
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


}

