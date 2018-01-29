package com.takahay.walker;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by takahay on 2018/01/28.
 */

public class HttpResHelper {
    public void postStatusCode( int code, int status )
    {
        try{
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            JSONObject jobj = new JSONObject();
            jobj.put("time", sdf.format(new Date()));
            jobj.put("statusCode", code);
            jobj.put("status", status);
            jobj.put("host", 1);
            new HttpResponsAsync().execute("api/processlogs/", jobj.toString());
        }
        catch (JSONException error) {
            Log.i("walker.HttpResHelper", error.toString());
        }
    }
}
