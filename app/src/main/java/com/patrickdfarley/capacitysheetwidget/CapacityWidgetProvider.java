package com.patrickdfarley.capacitysheetwidget;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.EditText;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.patrickdfarley.capacitysheetwidget.helpers.SharedPreferenceReader;
import com.patrickdfarley.capacitysheetwidget.helpers.TimerThreadManager;

import java.util.Arrays;

import pub.devrel.easypermissions.EasyPermissions;

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
    public static final String ENTRY_BUTTON_4 = "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_4";

    private static final String ACTION_TIMER_SET = "com.patrickdfarley.capacitysheetwidget.ACTION_TIMER_SET";

    //TODO: probably shouldn't have this both here and in MainActivity
    private static final String PREF_ACCOUNT_NAME = "Capacity Sheet Account Name";

    private static TimerTask timerTask = new TimerTask();


    // This should poll the spreadsheet and update the view
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called");

        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("IsInitialized",false)){
            Log.d(TAG, "OnUpdate: widget was not initialized. returning...");
            return;
        }


        final int N = appWidgetIds.length;

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i = 0; i < N; i++) {
            int appWidgetId = appWidgetIds[i];

            // Get the default view for the App Widget layout
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.capacity_appwidget);

            // create an intent with the action id ENTRY_BUTTON and set it to the onClick of a button
//            Intent intent = new Intent(ENTRY_BUTTON);
//            Intent intent = new Intent(context, CapacityWidgetProvider.class);
//            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
//
//            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,0, intent, PendingIntent.FLAG_UPDATE_CURRENT); //You need to specify a proper flag for the intent. Or else the intent will become deleted.
//            views.setOnClickPendingIntent(R.id.OneButton, pendingIntent);

//            appWidgetManager.updateAppWidget(appWidgetId, views);
//
//            // TODO: this credential doesn't have an account selected. How to portably return a credential that's ready?
//            mCredential = GoogleAccountCredential.usingOAuth2(
//                    context, Arrays.asList(SCOPES))
//                    .setBackOff(new ExponentialBackOff());
//
//            //TODO: this is unsafe
//            String accountName = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_ACCOUNT_NAME, null);
//            mCredential.setSelectedAccountName(accountName);
//
//            // TODO: test if credential is good (or just handle within the InitTask)
//
//            // TODO: do this work in a service instead? https://developer.android.com/guide/topics/appwidgets/#AppWidgetProvider
//            // do work updating view from spreadsheet
//            InitTask initTask = new InitTask(mCredential, context);
//            initTask.appWidgetManager = appWidgetManager;
//            initTask.appWidgetId = appWidgetId;
//            initTask.remoteViews = views;
//            initTask.execute();
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(TAG, "onReceive called where intent is " + intent.toString());
        super.onReceive(context, intent);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        // if it was an entry button click:
        if (ENTRY_BUTTON_0.equals(intent.getAction()) || ENTRY_BUTTON_1.equals(intent.getAction())
        || ENTRY_BUTTON_2.equals(intent.getAction()) || ENTRY_BUTTON_3.equals(intent.getAction())
        || ENTRY_BUTTON_4.equals(intent.getAction())){

            // record the value entered
            int amount = intent.getIntExtra("amount", 0);
            Log.d(TAG, "the amount was "+amount);

            // update the sharedprefs entry amount, by adding the new button's amount.
            int toReturn = amount + sharedPreferences.getInt("EntryAmount", 0);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("EntryAmount", toReturn);
            editor.apply();

            Toast toast = Toast.makeText(context, "current amount is " + toReturn, Toast.LENGTH_SHORT);
            toast.show();

            // Restart the 2s timer:
            TimerThreadManager.StartTimer();
        }

    }
    //region helper methods

    private void TimerUp(Context context){
        Toast toast = Toast.makeText(context, "Timer up!", Toast.LENGTH_SHORT);
        toast.show();
    }

    //endregion

}
