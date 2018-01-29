package com.takahay.walker;

import android.os.AsyncTask;
import android.util.Log;

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
import java.util.Date;

/**
 * Created by takahay on 2018/01/28.
 */

public class HttpResponsAsync extends AsyncTask<String, Void, String> {

    private static final String BASEURL = "https://ancient-dawn-23054.herokuapp.com/";
    private static final String TAG = "walker.HttpResponsAsync";

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // doInBackground前処理
    }

    @Override
    protected String doInBackground(String... params) {

        HttpURLConnection httpCon = null;
        StringBuffer result = new StringBuffer();
        try {
            Log.i(TAG, String.format("Post Location Data to Web. [%s%s]", BASEURL, params[0]));
            URL url = new URL(BASEURL + params[0]);

            httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setDoInput(true);
            httpCon.setUseCaches(false);
            httpCon.setRequestProperty("Content-Type", "application/json");
            httpCon.setRequestProperty("Accept", "application/json");
            httpCon.setRequestMethod("POST");

            OutputStream os = httpCon.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");

            Log.i(TAG, String.format("Post Json: %s", params[1]));
            osw.write(params[1]);
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
