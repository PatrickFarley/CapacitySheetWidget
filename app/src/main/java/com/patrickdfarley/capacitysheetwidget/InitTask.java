package com.patrickdfarley.capacitysheetwidget;
import android.app.Activity;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
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
    private static final int OFFSETTOP = 3;
    private static final int OFFSETBOTTOM = 2;


    private ProgressDialog mProgress;
    private Context context;
    private SharedPreferences sharedPreferences;
    private int categoryCount;

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

        // convert to points (row,col)
        Point[] dataRangePoints = A12Coords(dataRange);
        // record category count
        categoryCount = getRowCount(dataRangePoints);
        // Manually expand the range here: we want the first rows too.
        dataRangePoints[0].x -= OFFSETTOP;
        dataRangePoints[1].x += OFFSETBOTTOM;

        // create A1 string for use in Sheets API lookup
        final String finalRange = "'" + sheetName + "'!" + Coords2A1(dataRangePoints);
        Log.d(TAG,"finalRange: " + finalRange);
        Log.d(TAG,"sheet id: " + spreadsheetId);
        ValueRange response = this.sheetsService.spreadsheets().values()
                .get(spreadsheetId, finalRange)
                .execute();
        List<List<Object>> responseData = response.getValues();

        // TODO check for successful response and handle unsuccessful
        Log.d(TAG,"responsedata:" + responseData);
        return responseData;
    }

    // runs in the UI thread: update UI
    @Override
    protected void onPostExecute(List<List<Object>> responseData) {
        mProgress.hide();
        if (responseData == null || responseData.size() == 0) {
            Log.d(TAG, "No results returned.");
        } else {
            Log.d(TAG, TextUtils.join("\n", responseData));

            // create an updated RemoteViews
            RemoteViews newView = remoteViews;
            int currentWeekIndex = getCurrentWeekIndex(responseData);

            // add category amounts:
            for(int i = OFFSETTOP; i<categoryCount+ OFFSETTOP; i++){
                RemoteViews childView = new RemoteViews(context.getPackageName(),R.layout.category_item);
                String catAmount = responseData.get(i).size()>currentWeekIndex ? (String) (responseData.get(i).get(currentWeekIndex)) : "";
                childView.setTextViewText(R.id.CatAmount, catAmount);
                childView.setTextViewText(R.id.CatName,(String) responseData.get(i).get(0));

                newView.addView(R.id.CatsList, childView);
            }
            // add week info to DisplayBar:
            StringBuilder builder = new StringBuilder();
            builder.append("week ");
            builder.append(responseData.get(OFFSETTOP-1).get(currentWeekIndex));
            builder.append(" ");
            builder.append(responseData.get(OFFSETTOP+categoryCount+OFFSETBOTTOM - 1).get(currentWeekIndex));
            newView.setTextViewText(R.id.DisplayBar,builder);
            // update the app widget
            appWidgetManager.updateAppWidget(appWidgetID,newView);
        }
    }

    // This gets called if the user hasn't granted the app perms to their Google Sheets account
    @Override
    protected void onCancelled() {
        mProgress.hide();
        if (mLastError != null) {
            if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
//                showGooglePlayServicesAvailabilityErrorDialog(
//                        ((GooglePlayServicesAvailabilityIOException) mLastError)
//                                .getConnectionStatusCode());
            } else if (mLastError instanceof UserRecoverableAuthIOException) {
                ((Activity) context).startActivityForResult(
                        ((UserRecoverableAuthIOException) mLastError).getIntent(),
                        MainActivity.REQUEST_AUTHORIZATION);
            } else {
                Log.d(TAG,"The following error occurred:\n"
                        + mLastError.getMessage());
            }
        } else {
            Log.d(TAG,"Request cancelled.");
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

    private int getRowCount(Point[] dataRangePoints){
        if (dataRangePoints.length < 2) {
            throw new IllegalArgumentException("Need an array of at least 2 points!");
        }

        return dataRangePoints[1].x - dataRangePoints[0].x + 1;
    }

    private Point[] A12Coords(String A1dataString){
        String[] dataStringsArray = A1dataString.split(":");
        String rangeStartString = dataStringsArray[0];
        String rangeEndString = dataStringsArray[1];

        int startRow = Integer.parseInt(stripNonDigits(rangeStartString));
        int endRow = Integer.parseInt(stripNonDigits(rangeEndString));

        int startCol = columnLetter2Number(stripNonLetters(rangeStartString));
        int endCol = columnLetter2Number(stripNonLetters(rangeEndString));

        return new Point[]{new Point(startRow,startCol),new Point(endRow,endCol)};
    }

    private String Coords2A1(Point[] points) {
        if (points.length < 2){
            throw new IllegalArgumentException("Need an array of at least 2 points!");
        }
        StringBuilder builder = new StringBuilder();
        builder.append(number2ColumnLetter(points[0].y));   // column
        builder.append(points[0].x);    // row
        builder.append(":");
        builder.append(number2ColumnLetter(points[1].y));   // column
        builder.append(points[1].x);    // row

        return builder.toString();
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

    private String stripNonLetters(final CharSequence input){
        final StringBuilder sb = new StringBuilder(input.length());
        for(int i = 0; i < input.length(); i++){
            final char c = input.charAt(i);
            if(c > 64 && c < 91){
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public int columnLetter2Number(String inputColumnName) {
        int outputColumnNumber = 0;

        if (inputColumnName == null || inputColumnName.length() == 0) {
            throw new IllegalArgumentException("Input is not valid!");
        }

        int i = inputColumnName.length() - 1;
        int t = 0;
        while (i >= 0) {
            char curr = inputColumnName.charAt(i);
            outputColumnNumber = outputColumnNumber + (int) Math.pow(26, t) * (curr - 'A' + 1);
            t++;
            i--;
        }
        return outputColumnNumber;
    }

    public String number2ColumnLetter(int inputColumnNumber) {
        String outputColumnName = "";
        int Base = 26;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        int TempNumber = inputColumnNumber;
        while (TempNumber > 0) {
            int position = TempNumber % Base;
            outputColumnName = (position == 0 ? 'Z' : chars.charAt(position > 0 ? position - 1 : 0)) + outputColumnName;
            TempNumber = (TempNumber - 1) / Base;
        }
        return outputColumnName;
    }

    // endregion

}
