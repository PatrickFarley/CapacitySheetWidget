package com.patrickdfarley.capacitysheetwidget.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

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
     * @param original
     * @return
     */
    public RemoteViews ReadCategoryData(RemoteViews original){
        Log.d(TAG, "reading sharedprefs "+sharedPreferences.toString());
        RemoteViews toReturn = original;

        toReturn.getLayoutId();
        // clear cat views out of parent view
        toReturn.removeAllViews(R.id.CatsList);

        int categoryCount = sharedPreferences.getInt("CatCount", 0);
        for (int i=0; i<categoryCount; i++){
            // create a category_item childview
            RemoteViews childView = new RemoteViews(context.getPackageName(),R.layout.category_item);
            // TODO: the key strings being looked up here: should use a common helper method to construct these strings.
            childView.setTextViewText(R.id.CatAmount, sharedPreferences.getString("Cat"+i,null));
            childView.setTextViewText(R.id.CatName,sharedPreferences.getString("Cat"+i+"Name",null));
            toReturn.addView(R.id.CatsList, childView); // add childview to the CatsList element of the main view.
        }
        return toReturn;
    }
}


