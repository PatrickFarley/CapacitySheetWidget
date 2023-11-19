package com.patrickdfarley.capacitysheetwidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class CatsRemoteViewsService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new CatsRemoteViewsFactory(getApplicationContext(),intent);
    }
}

class CatsRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private SharedPreferences sharedPreferences;
    private static int mCount;
    private static List<CategoryScore> mCategoryScores = new ArrayList<CategoryScore>();
    private Context mContext;
    private int mAppWidgetId;
    private String TAG = "CatsRemoteViewsFactory";

    public CatsRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
        mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    // Initialize the data set.
    @Override
    public void onCreate() {

        Log.d(TAG,"onCreate called");
        // In onCreate() you setup any connections / cursors to your data source. Heavy lifting,
        // for example downloading or creating content etc, should be deferred to onDataSetChanged()
        // or getViewAt(). Taking more than 20 seconds in this call will result in an ANR.

        updateScoresFromPrefs();
    }


    @Override
    public void onDestroy() {
        // In onDestroy() you should tear down anything that was setup for your data source,
        // eg. cursors, connections, etc.
        mCategoryScores.clear();
    }

    @Override
    public int getCount() {
        Log.d(TAG,"getCount called and count is "+ mCount);
        return mCount;
    }

    // Given the position (index) of a WidgetItem in the array, use the item's text value in
    // combination with the app widget item XML file to construct a RemoteViews object.
    @Override
    public RemoteViews getViewAt(int position) {

        Log.d(TAG,"getViewAt called for "+position);
        // position will always range from 0 to getCount() - 1.
        // construct a remote views item based on our widget item xml file, and set the
        // text based on the position.
        RemoteViews childView = new RemoteViews(mContext.getPackageName(), R.layout.category_item);

        // retrieve the category info from static field
        CategoryScore catScore = mCategoryScores.get(position);
        childView.setTextViewText(R.id.CatAmount, catScore.score);
        childView.setTextViewText(R.id.CatName, catScore.name);

        // Next, we set a fill-intent which will be used to fill-in the pending intent template
        // which is set on the collection view in ListWidgetProvider.
        Bundle extras = new Bundle();
        extras.putInt(CapacityWidgetProvider.CAT_ID, position);
        Intent fillInIntent = new Intent();
        fillInIntent.putExtras(extras);
        // Make it possible to distinguish the individual on-click
        // action of a given item
        childView.setOnClickFillInIntent(R.id.CatItem, fillInIntent);

        // You can do heaving lifting in here, synchronously. For example, if you need to
        // process an image, fetch something from the network, etc., it is ok to do it here,
        // synchronously. A loading view will show up in lieu of the actual contents in the
        // interim.
//        try {
//            System.out.println("Loading view " + i);
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//            Log.d(TAG,e.toString());
//        }

        // Return the remote views object.
        Log.d (TAG, "returning view "+childView.toString());
        return childView;
    }

    @Override
    public RemoteViews getLoadingView() {
        // You can create a custom loading view (for instance when getViewAt() is slow.) If you
        // return null here, you will get the default loading view.
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public void onDataSetChanged() {
        // This is triggered when you call AppWidgetManager notifyAppWidgetViewDataChanged
        // on the collection view corresponding to this factory. You can do heavy lifting in
        // here, synchronously. For example, if you need to process an image, fetch something
        // from the network, etc., it is ok to do it here, synchronously. The widget will remain
        // in its current state while work is being done here, so you don't need to worry about
        // locking up the widget.
        Log.d(TAG,"onDataSetChanged called ...!");

        // you gotta update mcategoryscores from sharedprefs
        updateScoresFromPrefs();
    }

    private void updateScoresFromPrefs() {
        // populate the static list of categories and scores
        mCount = sharedPreferences.getInt("categoryCount",0);
        for (int i = 0; i < mCount; i++) {
            String catName = sharedPreferences.getString("Cat"+i+"Name","error");
            String catAmount = sharedPreferences.getString("Cat"+i,"error");
            mCategoryScores.add(i, new CategoryScore(catName, catAmount));
        }
    }
}
