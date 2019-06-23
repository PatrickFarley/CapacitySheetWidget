package com.patrickdfarley.capacitysheetwidget;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
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
import java.util.List;
import java.util.Scanner;

public class InitTask extends AsyncTask<Void, Void, List<List<Object>>> {

    private com.google.api.services.sheets.v4.Sheets sheetsService;
    private Exception mLastError = null;
    private static final String TAG = "InitTask";

    private ProgressDialog mProgress;
    private Context context;
    private SharedPreferences sharedPreferences;

    public AppWidgetManager appWidgetManager;
    public int appWidgetID;
    public RemoteViews remoteViews;

    /**
     * Class constructor
     * @param credential The GoogleAccountCredential, already prepared.
     * @param context The context of the calling class.
     */
    InitTask(GoogleAccountCredential credential, Context context) {
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        this.context = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        mProgress = new ProgressDialog(context);
        mProgress.setMessage("doing..");
        mProgress.setIndeterminate(false);
        mProgress.setMax(100);
        mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgress.setCancelable(true);

        // initialize the Sheets service
        sheetsService = new Sheets.Builder(
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
     * Fetches the spreadsheet data for a specific range
     * @return relevant cell data
     * @throws IOException
     */
    private List<List<Object>> getSheetData() throws IOException {

        String spreadsheetId = sharedPreferences.getString("SpreadsheetId","");
        String sheetName = sharedPreferences.getString("SheetName","");
        // TODO: The A1 range needs to be manually expanded here.
        String dataRange = sharedPreferences.getString("DataRange","");

        String range = sheetName + "!" + dataRange;
        ValueRange response = this.sheetsService.spreadsheets().values()
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

            // create an updated RemoteViews
            RemoteViews newView = remoteViews;
            int currentWeekIndex = getWeekIndex(output);

            // TODO: figure out how many cateogry rows to add
            // add category row values:
            for(int i=3;i<7;i++){
                RemoteViews childView = new RemoteViews(context.getPackageName(),R.layout.category_item);
                childView.setTextViewText(R.id.CatAmount,(String) output.get(i).get(currentWeekIndex));
                newView.addView(R.id.CatsList, childView);
            }
            newView.setTextViewText(R.id.OneButton,"ass");

            // update the app widget
            appWidgetManager.updateAppWidget(appWidgetID,newView);
        }
    }


    private int getWeekIndex(List<List<Object>> output){
        Scanner sc = new Scanner((String) output.get(0).get(0));
        double weekValue = sc.nextDouble();

        int weekNumber = (int)(Math.ceil(weekValue));
        int currentWeekIndex = weekNumber + 1;
        return currentWeekIndex;
    }
}
