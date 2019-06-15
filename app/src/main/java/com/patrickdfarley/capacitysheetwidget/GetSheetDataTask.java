package com.patrickdfarley.capacitysheetwidget;

import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.RemoteViews;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class GetSheetDataTask extends AsyncTask<Void, Void, List<List<Object>>> {

    private com.google.api.services.sheets.v4.Sheets mService = null;
    private Exception mLastError = null;
    private static final String TAG = "MakeRequestTask";

    private ProgressDialog mProgress;
    private Context context;

    public AppWidgetManager appWidgetManager;
    public int appWidgetID;
    public RemoteViews remoteViews;

    // class constructor
    GetSheetDataTask(GoogleAccountCredential credential, Context context) {
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        this.context = context;
        mProgress = new ProgressDialog(context);
        mProgress.setMessage("doing..");
        mProgress.setIndeterminate(false);
        mProgress.setMax(100);
        mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgress.setCancelable(true);

        mService = new Sheets.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("@string/app_name")
                .build();
    }


    /**
     * Background task to call Google Sheets API.
     * @param params no parameters needed for this task.
     */
    @Override
    protected List<List<Object>> doInBackground(Void... params) {
        try {
            return getSheetData();
        } catch (Exception e) {
            if (e instanceof UserRecoverableAuthIOException) {
//                Intent authorizationIntent = new Intent(this,
//                        GmailAuthorizationActivity.class)
//                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//
//                authorizationIntent.setAction("UserRecoverableAuthIOException");
//                authorizationIntent.putExtra("request_authorization",
//                        ((UserRecoverableAuthIOException) e).getIntent());
//
//                getContext().startActivity(authorizationIntent);
            }
            mLastError = e;
            cancel(true);
            return null;
        }
    }


    /**
     * Fetches the sheet data
     * @return relevant cell data
     * @throws IOException
     */
    private List<List<Object>> getSheetData() throws IOException {
        String spreadsheetId = "1Vg7gsEEl2sK8m9EXCNQmR60JkkMRCtwenWAJpuZuL5o";
        String range = "Spring / Summer!A1:AB13";
        ValueRange response = this.mService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        List<List<Object>> responseData = response.getValues();

        // TODO check for successful response and handle unsuccessful
        return responseData;
    }

    // runs in the UI thread: update UI
    @Override
    protected void onPostExecute(List<List<Object>> output) {
        mProgress.hide();
        if (output == null || output.size() == 0) {
            Log.d(TAG, "No results returned.");
        } else {

            Log.d(TAG, TextUtils.join("\n", output));

            RemoteViews newView = remoteViews;
            newView.setTextViewText(R.id.OneButton,"ass");

            appWidgetManager.updateAppWidget(appWidgetID,newView);
        }
    }


    private RemoteViews updateRemoteViews(RemoteViews originalView){

        return originalView;
    }
}
