package com.patrickdfarley.capacitysheetwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

/*
Sets up UI
 */
public class UIManager {

    private static final String TAG = "UIManager";
    private RemoteViews newView;
    private AppWidgetManager appWidgetManager;
    private int appWidgetId;


    private Context context;
    private SharedPreferences sharedPreferences;


    UIManager(Context context, int appWidgetId, AppWidgetManager appWidgetManager, RemoteViews remoteViews) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.appWidgetId = appWidgetId;
        this.appWidgetManager = appWidgetManager;
        this.newView = remoteViews;
        this.context = context;
    }

    /**
     * This reads from shared prefs. Ideally it would take an object param instead.
     */
    public void updateUI() {

        // update our RemoteViews category list from the newly updated sharedpreferences
        // Also adds their onclick listeners

        //region set CatsList remote adapter

        // Here we set up the intent which points to the CatsRemoteViewsService which will
        // provide the views for this collection.
        Intent rvIntent = new Intent(context, CatsRemoteViewsService.class);
        rvIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // When intents are compared, the extras are ignored, so we need to embed the extras
        // into the data so that the extras will not be ignored.
        rvIntent.setData(Uri.parse(rvIntent.toUri(rvIntent.URI_INTENT_SCHEME)));

        // set the CatsList's adapter to our CatsRemoteViewsService intent
        // This only calls the getViews method the first time it's done. after that, the adapter persists.
        newView.setRemoteAdapter(R.id.CatsList, rvIntent);
        Log.d(TAG, "setting view " + newView.toString() + " remoteAdapter to " + rvIntent.toString());

        // set what to display when the data is empty.
        newView.setEmptyView(R.id.CatsList, R.id.empty_view);


        // This section makes it possible for items to have individualized behavior.
        // It does this by setting up a pending intent template. Individuals items of a collection
        // cannot set up their own pending intents. Instead, the collection as a whole sets
        // up a pending intent template, and the individual items set a fillInIntent
        // to create unique behavior on an item-by-item basis.
        Intent catEntryIntent = new Intent(context, CapacityWidgetProvider.class);

        // Set the action for the intent.
        // When the user touches a particular view, it will have the effect of
        // broadcasting CAT_CLICK_ACTION.
        catEntryIntent.setAction(CapacityWidgetProvider.CAT_CLICK_ACTION);
        catEntryIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        catEntryIntent.setData(Uri.parse(catEntryIntent.toUri(Intent.URI_INTENT_SCHEME)));
        PendingIntent catEntryPendingIntent = PendingIntent.getBroadcast(context, 0, catEntryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        newView.setPendingIntentTemplate(R.id.CatsList, catEntryPendingIntent);
        //endregion

        // add week info to DisplayBar:
        StringBuilder builder = new StringBuilder();
        builder.append("week ");
        builder.append(sharedPreferences.getString("weekDate", "")); //TODO: use SharedPreferencesReader class for this, too.
        builder.append(" ");
        builder.append(sharedPreferences.getString("successScore", "")); // bottom row: success metric
        newView.setTextViewText(R.id.DisplayBar, builder); // add this string to the DisplayBar element of the view.

        //region Assign OnClickListeners
        // assign the DisplayBar's onclicklistener to trigger OnUpdate
        Intent intent;
        PendingIntent pendingIntent;
        // trigger an update for the given widget ID
        intent = new Intent(context, CapacityWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId}); // this will only pass the given app widget ID back to onUpdate.
        //You need to specify a proper flag for the intent. Or else the intent will become deleted.
        pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        newView.setOnClickPendingIntent(R.id.DisplayBar, pendingIntent);

        // minute entry buttons:
        // Each button sends an intent (carrying an integer amount) to trigger a response from the widgetprovider
        //TODO these shouldn't be hardcoded; and this should apparently use a Fill-in intent instead.
        int[] entryAmounts = {1, 5, 20, 60, 100};
        int[] entryIds = {R.id.OneButton, R.id.FiveButton, R.id.TwentyButton, R.id.SixtyButton, R.id.OneHundredButton};
        String[] entryActionIds = {"com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_0",
                "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_1",
                "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_2",
                "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_3",
                "com.patrickdfarley.capacitysheetwidget.ENTRY_BUTTON_4"};
        for (int i = 0; i < entryAmounts.length; i++) {
            intent = new Intent(context, CapacityWidgetProvider.class);
            intent.setAction(entryActionIds[i]); //TODO handle strings correctly
            intent.putExtra("amount", entryAmounts[i]);
            //You need to specify a proper flag for the intent. Or else the intent will become deleted.
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            newView.setOnClickPendingIntent(entryIds[i], pendingIntent);
            //Log.d(TAG, "Entry id " + entryIds[i] + " has pending intent " + pendingIntent.toString());
        }

        // assign the SettingsButton's onclicklistener to launch MainActivity
        intent = new Intent(context, MainActivity.class);

        //You need to specify a proper flag for the intent. Or else the intent will become deleted.
        pendingIntent = PendingIntent.getActivity(context,0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        newView.setOnClickPendingIntent(R.id.SettingsButton, pendingIntent);
        Log.d(TAG, "Settings button has pending intent "+pendingIntent.toString());


        //endregion

        // update the app widget
        appWidgetManager.updateAppWidget(appWidgetId, newView);
//        }
    }


}

