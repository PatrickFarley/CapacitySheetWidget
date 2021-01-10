package com.patrickdfarley.capacitysheetwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.util.List;

public class SheetsCallManager {

    private com.google.api.services.sheets.v4.Sheets sheetsService;
    private Exception mLastError = null;
    private static final String TAG = "InitTask";
    private static final int OFFSETTOP = 3;
    private static final int OFFSETBOTTOM = 2;
    private Context context;
    private SharedPreferences sharedPreferences;

    SheetsCallManager(GoogleAccountCredential credential, Context context) {
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        this.context = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }


    /**
     * get the current week index and the cat names; save to shared prefs for
     * other methods to use.
     *
     */
    public void SaveMetaDataToPrefs() {

        // get all sheet data
        List<List<Object>> responseData = GetAllSheetData();

        if (responseData == null || responseData.size() == 0) {
            Log.d(TAG, "No results returned!");
        } else {
            // display data again, now newline-delimited
            Log.d(TAG, TextUtils.join("\n", responseData));

            // save category count to preferences
            int categoryCount = responseData.size() - OFFSETBOTTOM - OFFSETTOP;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("categoryCount", categoryCount);

            // save category data to preferences
            String catName, catAmount;
            int currentWeekIndex = getCurrentWeekIndex(responseData);
            editor.putInt("CurrentWeekIndex", currentWeekIndex);

            // TODO: get just the cat names here. there's a cleaner way. go back a step and get only the cat cells
            // for each category row:
            for (int i = OFFSETTOP; i < categoryCount + OFFSETTOP; i++) {
                // get the category name for that row
                catName = (String) responseData.get(i).get(0);
                // get the current week's entered value, assuming it's withing the responseData's range:
                catAmount = responseData.get(i).size() > currentWeekIndex ? (String) (responseData.get(i).get(currentWeekIndex)) : "";
                editor.putString("Cat" + (i - OFFSETTOP), catAmount);
                editor.putString("Cat" + (i - OFFSETTOP) + "Name", catName);
                Log.d(TAG, "Category" + catName + " and value " + catAmount + " added to shared prefs.");
            }
            editor.apply();
        }
    }

    /**
     * This method returns ALL of the relevant sheet data, as simple values (not data).
     * @return
     */
    private List<List<Object>> GetAllSheetData() {
        //TODO: don't hardcode these strings.
        // fetch sheet identifier data from the sharedprefs
        String spreadsheetId = sharedPreferences.getString("SpreadsheetId", "");
        String sheetName = sharedPreferences.getString("SheetName", "");
        String dataRange = sharedPreferences.getString("DataRange", "");

        // convert to two points (row,col)
        Point[] dataRangePoints = A12Coords(dataRange);

        // Manually expand the range here: we want the very first row down to the succes row.
        dataRangePoints[0].x -= OFFSETTOP; // top-range row coordinate
        dataRangePoints[1].x += OFFSETBOTTOM; // bottom-range row coordinate

        // create an A1 string for use in Sheets API lookup
        final String finalRange = "'" + sheetName + "'!" + Coords2A1(dataRangePoints);
        Log.d(TAG, "finalRange: " + finalRange);

        try {
            // sheets API call: get all data within finalRange
            List<List<Object>> responseData = null;
            ValueRange response = this.sheetsService.spreadsheets().values()
                    .get(spreadsheetId, finalRange)
                    .execute();
            responseData = response.getValues();

            Log.d(TAG, "raw response data (whole range): " + responseData);
            return responseData;
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
            return null;
        }
    }



    /**
     * Using sharedprefs, get the current week's cat scores and success score and return
     * a Result object with these values.
     * @return
     */
    private WeekDataResult GetWeekData() {

        int currentWeekIndex = sharedPreferences.getInt("CurrentWeekIndex", 0);
        String spreadsheetId = sharedPreferences.getString("SpreadsheetId", "");
        String sheetName = sharedPreferences.getString("SheetName", "");
        String dataRange = sharedPreferences.getString("DataRange", "");
        int categoryCount = sharedPreferences.getInt("categoryCount", 0);

        // convert to two points (row,col)
        Point[] dataRangePoints = A12Coords(dataRange);
        dataRangePoints[0].y = currentWeekIndex;
        dataRangePoints[1].y = currentWeekIndex;
        dataRangePoints[1].x += OFFSETBOTTOM;

        final String finalRange = "'" + sheetName + "'!" + Coords2A1(dataRangePoints);

        List<List<Object>> weekData = null;
        try {
            // sheets API call: get all data within finalRange
            ValueRange response = this.sheetsService.spreadsheets().values()
                    .get(spreadsheetId, finalRange)
                    .execute();
            weekData = response.getValues();

        } catch (Exception e) {
            Log.d(TAG, e.getCause().toString());
            if (e instanceof UserRecoverableAuthIOException) {
                // TODO: So what's going on here? Why does that exception get thrown, and what does the following (recycled) code do to address it?
            }
            mLastError = e;
            return null;
        }

        WeekDataResult toReturn = new WeekDataResult();
        String catName, catScore;
        for (int i = 0; i < categoryCount; i++) {
            // get the current week's entered value, assuming it's withing the responseData's range:
            catScore = (String) weekData.get(i).get(0);
            catName = sharedPreferences.getString("Cat" + i, "");
            toReturn.addCategory(catName, catScore);
        }

        // get the current week's success score
        toReturn.SuccessScore = (String) weekData.get(categoryCount + OFFSETBOTTOM-1).get(0);

        return toReturn;
    }

// region Helper Methods

    private int getCurrentWeekIndex(List<List<Object>> output) {
//        Scanner sc = new Scanner((String) output.get(0).get(0));
//        double weekValue = sc.nextDouble();

        double weekValue = Double.parseDouble((String) output.get(0).get(0));
        int weekNumber = (int) (Math.ceil(weekValue));
        int currentWeekIndex = weekNumber + 1;

        Log.d(TAG, "current week index:" + currentWeekIndex);
        return currentWeekIndex;
    }

    private int getRowCount(Point[] dataRangePoints) {
        if (dataRangePoints.length < 2) {
            throw new IllegalArgumentException("Need an array of at least 2 points!");
        }

        return dataRangePoints[1].x - dataRangePoints[0].x + 1;
    }

    private Point[] A12Coords(String A1dataString) {
        String[] dataStringsArray = A1dataString.split(":");
        String rangeStartString = dataStringsArray[0];
        String rangeEndString = dataStringsArray[1];

        int startRow = Integer.parseInt(stripNonDigits(rangeStartString));
        int endRow = Integer.parseInt(stripNonDigits(rangeEndString));

        int startCol = columnLetter2Number(stripNonLetters(rangeStartString));
        int endCol = columnLetter2Number(stripNonLetters(rangeEndString));

        return new Point[]{new Point(startRow, startCol), new Point(endRow, endCol)};
    }

    private String Coords2A1(Point[] points) {
        if (points.length < 2) {
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


    private String stripNonDigits(final CharSequence input) {
        final StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (c > 47 && c < 58) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String stripNonLetters(final CharSequence input) {
        final StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (c > 64 && c < 91) {
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
