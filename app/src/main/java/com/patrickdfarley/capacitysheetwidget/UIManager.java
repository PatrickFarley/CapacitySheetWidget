package com.patrickdfarley.capacitysheetwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

import com.patrickdfarley.capacitysheetwidget.helpers.SharedPreferenceReader;

import java.util.List;

public class UIManager {

    private static final String TAG = "UIManager";
    private RemoteViews remoteViews;
    private AppWidgetManager appWidgetManager;
    private int appWidgetId;
    // TODO: these should universalized.
    private static final int OFFSETTOP = 3;
    private static final int OFFSETBOTTOM = 2;

    private Context context;
    private SharedPreferences sharedPreferences;


    UIManager(Context context, int appWidgetId, AppWidgetManager appWidgetManager, RemoteViews remoteViews){
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.appWidgetId = appWidgetId;
        this.appWidgetManager = appWidgetManager;
        this.remoteViews = remoteViews;
        this.context = context;
    }

    /**
     * This reads from shared prefs. Ideally it would take an object param instead.
     */
    public void updateUI() {
//        if (weekData == null || weekData.categories.size() == 0) {
//            Log.d(TAG, "No results passed!");
//        } else {
//            // display data again, now newline-delimited
//            Log.d(TAG, weekData.toString());

            // create an updated RemoteViews:
            RemoteViews newView = remoteViews;

            // update our RemoteViews category list from the newly updated preferences:
            newView = new SharedPreferenceReader(context).TransferCategoryData(newView);

            // add week info to DisplayBar:
            StringBuilder builder = new StringBuilder();
            builder.append("week ");
            builder.append(sharedPreferences.getString("weekDate","")); //TODO: use SharedPreferencesReader class for this, too.
            builder.append(" ");
            builder.append(sharedPreferences.getString("successScore","")); // bottom row: success metric
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

            // update the app widget
            appWidgetManager.updateAppWidget(appWidgetId, newView);
//        }
    }
}
