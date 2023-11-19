package com.patrickdfarley.capacitysheetwidget.helpers;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.patrickdfarley.capacitysheetwidget.CapacityWidgetProvider;
import com.patrickdfarley.capacitysheetwidget.CatsRemoteViewsService;
import com.patrickdfarley.capacitysheetwidget.R;

/**
 * This class takes a context, gets the sharedprefs from that context
 * Created by pdfarley on 9/9/2019.
 */

public class SharedPreferenceReader {
    private String TAG = "SharedPreferenceReader";
    private SharedPreferences sharedPreferences;
    private Context context;

    public SharedPreferenceReader(Context context){
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.context = context;
    }

    /**
     * Takes a RemoteViews of the capacity_appwidget layout and updates its category data from the
     * shared preferences. leaves other data untouched.
     * @param remoteViews
     * @return
     */
    public RemoteViews TransferCategoryData(RemoteViews remoteViews, int appWidgetId){

        Log.d(TAG, "reading from sharedprefs "+sharedPreferences.toString());

        Intent intent;
        String catName, catAmount;
        int categoryCount = sharedPreferences.getInt("CatCount", 0);
        for (int i=0; i<categoryCount; i++){
            // create a category_item childview
            RemoteViews childView = new RemoteViews(context.getPackageName(),R.layout.category_item);
            // TODO: the key strings being looked up here: should use a common helper method to construct these strings.

            catName = sharedPreferences.getString("Cat"+i+"Name",null);
            catAmount = sharedPreferences.getString("Cat"+i,null);

            childView.setTextViewText(R.id.CatAmount, catAmount );
            childView.setTextViewText(R.id.CatName, catName);

            remoteViews.addView(R.id.CatsList, childView); // add childview to the CatsList element of the main view.
        }
        return remoteViews;
    }
}


