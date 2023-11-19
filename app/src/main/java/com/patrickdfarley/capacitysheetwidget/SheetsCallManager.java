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
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
This class only interacts with google sheets and the sharedPrefs. It should be run in the background.
 */
public class SheetsCallManager {

    private com.google.api.services.sheets.v4.Sheets sheetsService;
    private Exception mLastError = null;
    private static final String TAG = "SheetsCallManager";
    private static final int OFFSETTOP = 3;
    private static final int OFFSETBOTTOM = 1;
    private Context context;
    private SharedPreferences sharedPrefs;

    SheetsCallManager(GoogleAccountCredential credential, Context context) {
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        this.context = context;

        // initialize the Sheets service
        sheetsService = new Sheets.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("@string/app_name")
                .build();

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    // TODO: you need error handling for index-out-of-bounds exceptions. Anywhere that data is retrieved, you need to handle errors.


    /**
     * get the current week index and the cat names; save to shared prefs for
     * other methods to use.
     *
     */
    public void saveMetaDataToPrefs() {

        // get all sheet data
        List<List<Object>> responseData = GetSheetDataInRange(false);

        if (responseData == null || responseData.size() == 0) {
            Log.d(TAG, "No results returned!");
        } else {
            // display data again, now newline-delimited
            Log.d(TAG, TextUtils.join("\n", responseData));

            // save category count to preferences
            int categoryCount = responseData.size() - OFFSETBOTTOM - OFFSETTOP;
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putInt("categoryCount", categoryCount);

            // save current week index to preferences
            int currentWeekIndex = getCurrentWeekIndex(responseData);
            editor.putInt("CurrentWeekIndex", currentWeekIndex);

            // save current week date to preferences
            String weekDate = String.valueOf(responseData.get(OFFSETTOP-1).get(currentWeekIndex));
            editor.putString("weekDate",weekDate);

            // save success score to preferences
            String successScore = String.valueOf(responseData.get(OFFSETTOP+categoryCount+OFFSETBOTTOM - 1).get(currentWeekIndex)); // bottom row: success metric
            editor.putString("successScore",successScore);

            // save category data to preferences
            String catName, catAmount;
            // TODO: get just the cat names here. there's a cleaner way. go back a step and get only the cat cells
            // for each category row:
            for (int i = OFFSETTOP; i < categoryCount + OFFSETTOP; i++) {
                // get the category name for that row
                catName = String.valueOf(responseData.get(i).get(0));
                // get the current week's entered value, assuming it's withing the responseData's range:
                catAmount = responseData.get(i).size() > currentWeekIndex ? String.valueOf((responseData.get(i).get(currentWeekIndex))) : "";
                editor.putString("Cat" + (i - OFFSETTOP), catAmount);
                editor.putString("Cat" + (i - OFFSETTOP) + "Name", catName);
                Log.d(TAG, "Category" + catName + " and value " + catAmount + " added to shared prefs.");
            }
            editor.apply();
        }
    }


    public void addMinuteData(int catID, int entryAmount){
        // get all sheet data
        List<List<Object>> rawResponseData = GetSheetDataInRange(true);

        String spreadsheetId = sharedPrefs.getString("SpreadsheetId", "");
        String sheetName = sharedPrefs.getString("SheetName", "");
        String dataRange = sharedPrefs.getString("DataRange", "");


        // pinpoint the particular category data we want
        int catRow = catID + OFFSETTOP;
        int currentWeekIndex = sharedPrefs.getInt("CurrentWeekIndex", 0);
        Point cellPoint = new Point(catRow,currentWeekIndex);
        Log.d(TAG,"point is "+ cellPoint.toString());
        Log.d(TAG, "going to get point "+ cellPoint.x + cellPoint.y + " for responseData " + rawResponseData.toString());
        String catData = String.valueOf(rawResponseData.get(cellPoint.x).get(cellPoint.y));


        String newCatData = catData + "+" + entryAmount;
        // convert catData to a formula if it isn't already:
        char firstChar = newCatData.charAt(0);
        if (firstChar != '+' && firstChar != '='){
            newCatData = "=" + newCatData;
        }

        // modify the category data
        List<List<Object>> values = Arrays.asList(
                Arrays.asList(
                        (Object)newCatData
                )
        );
        ValueRange newValueRange = new ValueRange().setValues(values);

        // convert cell point to use the 1-beginning index format of Sheets:
        cellPoint.x += 1;
        cellPoint.y += 1;
        final String finalRange = "'" + sheetName + "'!" + Point2A1(cellPoint);
        Log.d(TAG,"final range is "+finalRange);

        // write the category data
        try {
            // sheets API call:
            UpdateValuesResponse response = sheetsService.spreadsheets().values()
                    .update(spreadsheetId, finalRange, newValueRange).setValueInputOption("USER_ENTERED")
                    .execute();
            Log.d(TAG, "New cat data "+ newCatData + " written successfully!");
            // TODO: log the operation in the undo stack here.

            // TODO: call OnUpdate here.

            return;
        } catch (Exception e) {
            Log.d(TAG, "data write error is: " + String.valueOf(e.getCause()));
            if (e instanceof UserRecoverableAuthIOException) {
                // TODO: So what's going on here? Why does that exception get thrown?
            }
            mLastError = e;
            return;
        }

    }


    /**
     * This method returns ALL of the relevant sheet data, as simple values (not data).
     * It is private - the raw data is handled by other methods in this class.
     * @return
     */
    private List<List<Object>> GetSheetDataInRange(boolean raw) {
        //TODO: don't hardcode these strings. or at least put them in constructor
        // fetch sheet identifier data from the sharedprefs
        String spreadsheetId = sharedPrefs.getString("SpreadsheetId", "");
        String sheetName = sharedPrefs.getString("SheetName", "");
        String dataRange = sharedPrefs.getString("DataRange", "");

        // convert to two points (row,col)
        Point[] dataRangePoints = A12Coords(dataRange);

        // Manually expand the range here: we want the very first row down to the success row.
        dataRangePoints[0].x -= OFFSETTOP; // top-range row coordinate
        dataRangePoints[1].x += OFFSETBOTTOM; // bottom-range row coordinate

        // create an A1 string for use in Sheets API lookup
        final String finalRange = "'" + sheetName + "'!" + Coords2A1(dataRangePoints);

        Log.d(TAG, "finalRange (coords): " + dataRangePoints[0].x + "," + dataRangePoints[0].y + " - " + dataRangePoints[1].x + "," + dataRangePoints[1].y);
        Log.d(TAG, "finalRange (A1): " + finalRange);

        try {
            String valueRenderOption = raw ? "FORMULA" : "FORMATTED_VALUE";
            // sheets API call: get all data within finalRange
            List<List<Object>> responseData = null;
            ValueRange response = this.sheetsService.spreadsheets().values()
                    .get(spreadsheetId, finalRange).setValueRenderOption(valueRenderOption)
                    .execute();
            // issue here is that empty cells get left out.
            responseData = response.getValues();

            //a data-cleaning method that looks for rows or columns that were cut off, and inserts empty strings there.
            List<List<Object>> responseDataCleaned = CleanResponseData(responseData,dataRangePoints);

            Log.d(TAG, "response data (whole range (cleaned)): " + responseDataCleaned);
            return responseDataCleaned;
        } catch (Exception e) {
            Log.d(TAG, "data get error is " + (e.getCause()));
            if (e instanceof UserRecoverableAuthIOException) {
//                // TODO: So what's going on here? Why does that exception get thrown, and what does the following (recycled) code do to address it?
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
     * TODO: This doesn't seem to be necessary.
     * @return
     */
    private WeekDataResult GetWeekData() {

        int currentWeekIndex = sharedPrefs.getInt("CurrentWeekIndex", 0);
        String spreadsheetId = sharedPrefs.getString("SpreadsheetId", "");
        String sheetName = sharedPrefs.getString("SheetName", "");
        String dataRange = sharedPrefs.getString("DataRange", "");
        int categoryCount = sharedPrefs.getInt("categoryCount", 0);

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
            catScore = String.valueOf(weekData.get(i).get(0));
            catName = sharedPrefs.getString("Cat" + i, "");
            toReturn.addCategory(catName, catScore);
        }

        // get the current week's success score
        toReturn.SuccessScore = String.valueOf(weekData.get(categoryCount + OFFSETBOTTOM-1).get(0));

        return toReturn;
    }

// region Helper Methods

    private List<List<Object>> CleanResponseData(List<List<Object>> responseData, Point[] dataRangePoints) {
        List<List<Object>> filledValues = new ArrayList<>();

        // Determine the number of columns in the range
        int maxColumns = dataRangePoints[1].y - dataRangePoints[0].y;
        Log.d(TAG, "max columns is "+maxColumns);

        // for each row in response data:
        for (List<Object> row : responseData) {
            // Calculate the number of null values to add to reach the desired length
            int paddingSize = Math.max(0, maxColumns - row.size());

            // Add null values to the end of the row
            for (int i = 0; i < paddingSize; i++) {
                row.add("");
            }
            filledValues.add(row);
        }
        return filledValues;
    }

    private int getCurrentWeekIndex(List<List<Object>> output) {
//        Scanner sc = new Scanner((String) output.get(0).get(0));
//        double weekValue = sc.nextDouble();

        double weekValue = Double.parseDouble(String.valueOf(output.get(0).get(0)));
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
        builder.append(Point2A1(points[0]));
        builder.append(":");
        builder.append(Point2A1(points[1]));

        return builder.toString();
    }

    private String Point2A1(Point point) {
        StringBuilder builder = new StringBuilder();
        builder.append(number2ColumnLetter(point.y));   // column
        builder.append(point.x);    // row

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

    private int columnLetter2Number(String inputColumnName) {
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

    private String number2ColumnLetter(int inputColumnNumber) {
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
