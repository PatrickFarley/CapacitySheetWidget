package com.patrickdfarley.capacitysheetwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.patrickdfarley.capacitysheetwidget.helpers.TimerThreadManager;

import java.util.Arrays;
import java.util.concurrent.Executors;

/**
 * Created by pdfarley on 3/5/2019.
 */

public class CapacityWidgetProvider extends AppWidgetProvider {

    GoogleAccountCredential mCredential;
    private static final String[] SCOPES = {SheetsScopes.SPREADSHEETS_READONLY, SheetsScopes.SPREADSHEETS};
    private static final String TAG = "CapacityWidgetProvider";

    public static final String ENTRY_BUTTON_0 = "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_0";
    public static final String ENTRY_BUTTON_1 = "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_1";
    public static final String ENTRY_BUTTON_2 = "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_2";
    public static final String ENTRY_BUTTON_3 = "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_3";
    public static final String CAT_CLICK_ACTION = "com.patrickdfarley.capacitysheetwidget.CAT_ENTRY_ACTION";

    public static final String CAT_ID = "com.patrickdfarley.capacitysheetwidget.CAT_ID";
    private static final String ACTION_TIMER_SET = "com.patrickdfarley.capacitysheetwidget.ACTION_TIMER_SET";
    public static final String MANUAL_UPDATE = "com.patrickdfarley.capacitysheetwidget.MANUAL_UPDATE";
    public static final String MANUAL_UPDATE_FAST = "com.patrickdfarley.capacitysheetwidget.MANUAL_UPDATE_FAST";


    //TODO: probably shouldn't have this both here and in MainActivity
    private static final String PREF_ACCOUNT_NAME = "Capacity Sheet Account Name";


    /**
     * this is triggered on a timetable, OR can be triggered manually by an intent. it's triggered for some set of app widget IDs. It should update the UI
     *
     * @param context
     * @param appWidgetManager
     * @param appWidgetIds
     */
    public void onUpdate(final Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called");

        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("IsInitialized", false)) {
            Log.d(TAG, "OnUpdate: widget was not initialized. returning...");
            return;
        }

        updateUIFromSheets(context, appWidgetManager, appWidgetIds);


    }



    /**
     * receives many kinds of inputs.
     *
     * @param context
     * @param intent
     */
    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(TAG, "onReceive called where intent is " + intent.toString());

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        // If it was a manual update request (full):
        if (intent.getAction().equals(MANUAL_UPDATE)) {
            Log.d(TAG, "manual update triggered");

            // TODO: I'm not sure if I'm getting these values the right way:
            int[] appWidgetIds = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(new ComponentName(context, CapacityWidgetProvider.class));

            if (appWidgetIds.length > 0) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

                updateUIFromSheets(context,appWidgetManager,appWidgetIds);
            }
        }

        // If it was a manual update request (quick - just from sharedPrefs):
        if (intent.getAction().equals(MANUAL_UPDATE_FAST)) {
            Log.d(TAG, "manual update triggered (fast)");

            // TODO: I'm not sure if I'm getting these values the right way:
            int[] appWidgetIds = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(new ComponentName(context, CapacityWidgetProvider.class));

            if (appWidgetIds.length > 0) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

                updateUI(context,appWidgetManager,appWidgetIds);
            }
        }

        // if it was an entry button click:
        if (ENTRY_BUTTON_0.equals(intent.getAction()) || ENTRY_BUTTON_1.equals(intent.getAction())
                || ENTRY_BUTTON_2.equals(intent.getAction()) || ENTRY_BUTTON_3.equals(intent.getAction())) {

            // trigger vibration
            // vibratePhone(context);

            // record the value entered
            int amount = intent.getIntExtra("amount", 0);
            Log.d(TAG, "the amount was " + amount);


            // update the sharedprefs entry amount, by adding the new button's amount.
            int toReturn = amount + sharedPreferences.getInt("EntryAmount", 0);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("EntryAmount", toReturn);
            editor.apply();

            // display the value on the widget:
            int[] appWidgetIds = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(new ComponentName(context, CapacityWidgetProvider.class));
            if (appWidgetIds.length > 0) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                updateEntryCounterDisplay(context, appWidgetManager, appWidgetIds);
            }

            //Toast toast = Toast.makeText(context, "current amount is " + toReturn, Toast.LENGTH_SHORT);
            //toast.show();

            // Restart the timer; only when timer finishes does the value get sent to the sheet.
            // TODO: This is unsafe!; this class doesn't have access to all the credential checks that MainActivity has.
            mCredential = GoogleAccountCredential.usingOAuth2(
                    context, Arrays.asList(SCOPES))
                    .setBackOff(new ExponentialBackOff());
            String accountName = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_ACCOUNT_NAME, null);
            mCredential.setSelectedAccountName(accountName);
            Log.d(TAG, "outer thread credential is "+mCredential);
            final SheetsCallManager sheetsCallManager = new SheetsCallManager(mCredential, context);

            Runnable timerRunnable = constructTimerRunnable(sharedPreferences, context, sheetsCallManager);
            TimerThreadManager.getInstance().RestartTimer(timerRunnable);

        }


        // if it was a CategoryName click:
        if (CAT_CLICK_ACTION.equals(intent.getAction())) {
            // int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

            int catId = intent.getIntExtra(CAT_ID, 0);

            //Toast.makeText(context, "Item" + catId + " selected", Toast.LENGTH_SHORT).show();

            // save the new cat selection to sharedPrefs
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("CatId", catId);
            editor.apply();

            // also update the UI:
            // trigger an onReceive to update UI:
            Intent updateIntent = new Intent(context, CapacityWidgetProvider.class);
            updateIntent.setAction(CapacityWidgetProvider.MANUAL_UPDATE_FAST);
            context.sendBroadcast(updateIntent);
        }

        super.onReceive(context, intent);
    }



    private void updateUI(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
        // This just triggers UIManager's UI update (For sharedPrefs that are already saved) - it doesn't query sheets, so it's quicker

        Log.d(TAG,"updateUI called");
        final int N = appWidgetIds.length;

        // Get the default view for the App Widget layout
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.capacity_appwidget);

        // get a Credential
        // TODO: This is unsafe; this class doesn't have access to all the credential checks that MainActivity has.
        mCredential = GoogleAccountCredential.usingOAuth2(
                context, Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        String accountName = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_ACCOUNT_NAME, null);
        mCredential.setSelectedAccountName(accountName);

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i = 0; i < N; i++) {
            final int appWidgetId = appWidgetIds[i];

            // get sheet data and update UI:
            final UIManager uIManager = new UIManager(context, appWidgetId, appWidgetManager, views);

            // need to notify the CatsRemoteViewsService that cat data was updated
            //AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.CatsList);
            Log.d(TAG,"notifyAppWidgetViewDataChanged");

            uIManager.updateUI();
        }
    }

    private void updateUIFromSheets(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {

        // This method actually pulls sheet data (saveMetaDataToPrefs) as well as triggers the UIManager's UI update.

        Log.d(TAG,"updateUIFromSheets called");
        final int N = appWidgetIds.length;

        // Get the default view for the App Widget layout
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.capacity_appwidget);

        // get a Credential
        // TODO: This is unsafe; this class doesn't have access to all the credential checks that MainActivity has.
        mCredential = GoogleAccountCredential.usingOAuth2(
                context, Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        String accountName = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_ACCOUNT_NAME, null);
        mCredential.setSelectedAccountName(accountName);

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i = 0; i < N; i++) {
            final int appWidgetId = appWidgetIds[i];

            // get sheet data and update UI:
            final UIManager uIManager = new UIManager(context, appWidgetId, appWidgetManager, views);

            // we create this mainThreadHandler to run after the async sheets task runs. It executes in the main thread.
            final Handler mainThreadHandler = new Handler(Looper.getMainLooper()); // This is a deprecated method; might want to update the Android version
            Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    SheetsCallManager sheetsCallManager = new SheetsCallManager(mCredential, context);
                    try {
                        sheetsCallManager.saveMetaDataToPrefs();
                    } catch (Exception e){
                        return;
                    }

                    // and then need to notify the CatsRemoteViewsService that cat data was updated
                    //AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.CatsList);
                    Log.d(TAG,"notifyAppWidgetViewDataChanged");

                    // then call UI update in the main thread
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            uIManager.updateUI();
                        }
                    });
                }
            });
        }
    }

    private void updateEntryCounterDisplay(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds){

        final int N = appWidgetIds.length;

        // Get the default view for the App Widget layout
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.capacity_appwidget);

        // get a Credential
        // TODO: This is unsafe; this class doesn't have access to all the credential checks that MainActivity has.
        mCredential = GoogleAccountCredential.usingOAuth2(
                context, Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        String accountName = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_ACCOUNT_NAME, null);
        mCredential.setSelectedAccountName(accountName);

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i = 0; i < N; i++) {
            final int appWidgetId = appWidgetIds[i];

            // get sheet data and update UI:
            final UIManager uIManager = new UIManager(context, appWidgetId, appWidgetManager, views);

            uIManager.updateEntryCounterDisplay();
        }
    }

    private void vibratePhone(Context context) {
        Log.d(TAG,"vibratePhone called");
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            // Deprecated in API 26
            vibrator.vibrate(25);
        }
    }
    //region helper methods

    private void TimerUp(Context context) {
        Toast toast = Toast.makeText(context, "Timer up!", Toast.LENGTH_SHORT);
        toast.show();
    }

    private Runnable constructTimerRunnable(final SharedPreferences sharedPrefs, final Context context, final SheetsCallManager sheetsCallManager) {

        final int catId = sharedPrefs.getInt("CatId", -1);
        final int entryAmount = sharedPrefs.getInt("EntryAmount", -1);

        return new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Executing Write operation!");

                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sheetsCallManager.addMinuteData(catId, entryAmount);
                        } catch (Exception e){
                            return;
                        }

                        // zero out the EntryAmount
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        editor.putInt("EntryAmount", 0);
                        editor.apply();

                        // trigger an onReceive to update UI:
                        Intent updateIntent = new Intent(context, CapacityWidgetProvider.class);
                        updateIntent.setAction(CapacityWidgetProvider.MANUAL_UPDATE);
                        context.sendBroadcast(updateIntent);

                    }
                });
            }
        };
    }


    //endregion

}
