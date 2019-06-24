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
        String dataRange = sharedPreferences.getString("DataRange","");

        // Manually expand the range here: we want the first rows too.
        final String finalRange = "'" + sheetName + "'!" + dataRange.replaceFirst("\\d+","1");;
        Log.d(TAG,finalRange);

        ValueRange response = this.sheetsService.spreadsheets().values()
                .get(spreadsheetId, finalRange)
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
            int currentWeekIndex = getCurrentWeekIndex(output);

            // figure out how many category rows to add:
            //String dataRange = sharedPreferences.getString("DataRange","");
            int categoryCount = getRowCount(output);

            // add category amounts:
            int offset = 3; //TODO: this should be saved more carefully
            for(int i=offset;i<categoryCount+offset;i++){
                RemoteViews childView = new RemoteViews(context.getPackageName(),R.layout.category_item);
                String catAmount = output.get(i).size()>currentWeekIndex ? (String) (output.get(i).get(currentWeekIndex)) : "";
                childView.setTextViewText(R.id.CatAmount, catAmount);
                newView.addView(R.id.CatsList, childView);
            }
            // sanity test
            newView.setTextViewText(R.id.OneButton,"ass");

            // update the app widget
            appWidgetManager.updateAppWidget(appWidgetID,newView);
        }
    }

    // region Helper Methods

    private int getCurrentWeekIndex(List<List<Object>> output){
//        Scanner sc = new Scanner((String) output.get(0).get(0));
//        double weekValue = sc.nextDouble();

        double weekValue = Double.parseDouble((String) output.get(0).get(0));
        int weekNumber = (int)(Math.ceil(weekValue));
        int currentWeekIndex = weekNumber + 1;

        Log.d(TAG, "" + currentWeekIndex);
        return currentWeekIndex;
    }

    private int getRowCount(List<List<Object>> responseData){
//        String[] dataStringsArray = dataRangeString.split(":");
//        String rangeStartString = dataStringsArray[0];
//        String rangeEndString = dataStringsArray[1];
//
//        int startRow = Integer.parseInt(stripNonDigits(rangeStartString));
//        int endRow = Integer.parseInt(stripNonDigits(rangeEndString));
//
//        return endRow - startRow + 1;

        return responseData.size() - 3;
    }

    private String stripNonDigits(final CharSequence input){
        final StringBuilder sb = new StringBuilder(input.length());
        for(int i = 0; i < input.length(); i++){
            final char c = input.charAt(i);
            if(c > 47 && c < 58){
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // endregion

}
