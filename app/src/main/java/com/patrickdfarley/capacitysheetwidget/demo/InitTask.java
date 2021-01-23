package com.patrickdfarley.capacitysheetwidget.demo;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
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
import com.patrickdfarley.capacitysheetwidget.CapacityWidgetProvider;
import com.patrickdfarley.capacitysheetwidget.MainActivity;
import com.patrickdfarley.capacitysheetwidget.R;
import com.patrickdfarley.capacitysheetwidget.helpers.SharedPreferenceReader;

import java.io.IOException;
import java.util.List;

/**
 * An asynchronous task that handles the initial Google Sheets API read call.
 * Placing the API calls in their own task ensures the UI stays responsive.
 */
public class InitTask extends AsyncTask<Void, Void, List<List<Object>>> {

    private com.google.api.services.sheets.v4.Sheets sheetsService;
    private Exception mLastError = null;
    private static final String TAG = "InitTask";
    private static final int OFFSETTOP = 3;
    private static final int OFFSETBOTTOM = 2;


    private ProgressDialog mProgress;
    private Context context;
    private SharedPreferences sharedPreferences;

    // TODO these should probably be getter/setter instead of public vars (or done in the constructor?)
    public AppWidgetManager appWidgetManager;
    public int appWidgetId;
    public RemoteViews remoteViews;

    /**
     * Class constructor
     * @param credential The GoogleAccountCredential, already prepared.
     * @param context The context of the calling class.
     */
    InitTask(GoogleAccountCredential credential, Context context) {
        Log.d(TAG, "InitTask created with credential" + credential.toString());

        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        this.context = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Progress bar stuff you might not use
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
        Log.d(TAG, "doInBackground() called");
        try {
            return getSheetData();
        } catch (Exception e) {
            Log.d(TAG, e.getCause().toString());
            if (e instanceof UserRecoverableAuthIOException) {
                // TODO: So what's going on here? Why does that exception get thrown, and what does the following (recycled) code do to address it?
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

        //TODO: don't hardcode these strings.
        // fetch sheet identifier data from the sharedprefs
        String spreadsheetId = sharedPreferences.getString("SpreadsheetId","");
        String sheetName = sharedPreferences.getString("SheetName","");
        String dataRange = sharedPreferences.getString("DataRange","");

        // convert to points (row,col)
        Point[] dataRangePoints = A12Coords(dataRange);

        // record category count
        //categoryCount = getRowCount(dataRangePoints);

        // Manually expand the range here: we want the very first row down to the succes row.
        dataRangePoints[0].x -= OFFSETTOP; // top-range row coordinate
        dataRangePoints[1].x += OFFSETBOTTOM; // bottom-range row coordinate

        // create an A1 string for use in Sheets API lookup
        final String finalRange = "'" + sheetName + "'!" + Coords2A1(dataRangePoints);
        Log.d(TAG,"finalRange: " + finalRange);

        // sheets API call: get all data within finalRange
        List<List<Object>> responseData = null;
        ValueRange response = this.sheetsService.spreadsheets().values()
                .get(spreadsheetId, finalRange)
                .execute();
        responseData = response.getValues();

        Log.d(TAG,"raw response data (whole range): " + responseData);
        return responseData;
    }

    // This runs in the UI thread: update UI
    @Override
    protected void onPostExecute(List<List<Object>> responseData) {
        mProgress.hide();
        if (responseData == null || responseData.size() == 0) {
            Log.d(TAG, "No results returned!");
        } else {
            // display data again, now newline-delimited
            Log.d(TAG, TextUtils.join("\n", responseData));

            // create an updated RemoteViews:
            RemoteViews newView = remoteViews;

            // save category count to preferences
            int categoryCount = responseData.size() - OFFSETBOTTOM - OFFSETTOP;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("CatCount",categoryCount);

            // save category data to preferences
            String catName, catAmount;
            int currentWeekIndex = getCurrentWeekIndex(responseData);
            // for each category row:
            for(int i = OFFSETTOP; i<categoryCount+ OFFSETTOP; i++){
                // get the category name for that row
                catName = (String) responseData.get(i).get(0);
                // get the current week's entered value, assuming it's withing the responseData's range:
                catAmount = responseData.get(i).size()>currentWeekIndex ? (String) (responseData.get(i).get(currentWeekIndex)) : "";
                editor.putString("Cat"+(i-OFFSETTOP),catAmount);
                editor.putString("Cat"+(i-OFFSETTOP) + "Name",catName);
                Log.d(TAG, "Category" + catName + " and value " + catAmount + " added to shared prefs.");
            }
            editor.apply();

            // update our RemoteViews category list from the newly updated preferences:
            newView = new SharedPreferenceReader(context).TransferCategoryData(newView);


            // add week info to DisplayBar:
            StringBuilder builder = new StringBuilder();
            builder.append("week ");
            builder.append(responseData.get(OFFSETTOP-1).get(currentWeekIndex));
            builder.append(" ");
            builder.append(responseData.get(OFFSETTOP+categoryCount+OFFSETBOTTOM - 1).get(currentWeekIndex)); // bottom row: success metric
            newView.setTextViewText(R.id.DisplayBar,builder); // add this string to the DisplayBar element of the view.

        //region Assign OnClickListeners
            // assign the DisplayBar's onclicklistener to trigger OnUpdate
            Intent intent;
            PendingIntent pendingIntent;
            // trigger an update for the given widget ID
            intent = new Intent(context, CapacityWidgetProvider.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra( AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] { appWidgetId } ); // this will only pass the given app widget ID back to onUpdate.
            //You need to specify a proper flag for the intent. Or else the intent will become deleted.
            pendingIntent = PendingIntent.getBroadcast(context,0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            newView.setOnClickPendingIntent(R.id.DisplayBar, pendingIntent);

            // minute entry buttons:
            // Each button sends an intent (carrying an integer amount) to trigger a response from the widgetprovider
            //TODO these shouldn't be hardcoded
            int[] entryAmounts = {1,5,20,60,100};
            int[] entryIds = {R.id.OneButton, R.id.FiveButton, R.id.TwentyButton, R.id.SixtyButton, R.id.OneHundredButton};
            String[] entryActionIds = {"com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_0",
                    "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_1",
                    "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_2",
                    "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_3",
                    "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_4"};

            for (int i=0;i<entryAmounts.length;i++) {
                intent = new Intent(context, CapacityWidgetProvider.class);
                intent.setAction(entryActionIds[i]); //TODO handle strings correctly
                intent.putExtra("amount", entryAmounts[i]);
                //You need to specify a proper flag for the intent. Or else the intent will become deleted.
                pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                newView.setOnClickPendingIntent(entryIds[i], pendingIntent);
                Log.d(TAG, "Entry id "+ entryIds[i] + " has pending intent "+pendingIntent.toString());
            }

            // TODO Settings button onclicklistener
        //endregion

            // update the app widget (This triggers OnUpdate)
            appWidgetManager.updateAppWidget(appWidgetId, newView);
        }
    }

    // This gets called if the user hasn't granted the app perms to their Google Sheets account
    // (different from granting perms to the google account itself!)
    @Override
    protected void onCancelled() {
        mProgress.hide();
        if (mLastError != null) {
            if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                // TODO: this should've been handled elsewhere so prob doesn't need to be handled here.
//                showGooglePlayServicesAvailabilityErrorDialog(
//                        ((GooglePlayServicesAvailabilityIOException) mLastError)
//                                .getConnectionStatusCode());
            } else if (mLastError instanceof UserRecoverableAuthIOException) {
                ((Activity) context).startActivityForResult(
                        ((UserRecoverableAuthIOException) mLastError).getIntent(),
                        MainActivity.REQUEST_AUTHORIZATION);
                // but this task doesn't handle the return. So it gets the perms, but doesn't rerun itself immediately.
            } else {
                Log.d(TAG,"The following unhandled error occurred:\n"
                        + mLastError.getMessage());
            }
        } else {
            Log.d(TAG,"InitTask cancelled. No LastError reported.");
        }
    }


    // region Helper Methods

    private int getCurrentWeekIndex(List<List<Object>> output){
//        Scanner sc = new Scanner((String) output.get(0).get(0));
//        double weekValue = sc.nextDouble();

        double weekValue = Double.parseDouble((String) output.get(0).get(0));
        int weekNumber = (int)(Math.ceil(weekValue));
        int currentWeekIndex = weekNumber + 1;

        Log.d(TAG, "current week index:" + currentWeekIndex);
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
