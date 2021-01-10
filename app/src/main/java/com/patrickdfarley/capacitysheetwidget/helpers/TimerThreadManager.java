package com.patrickdfarley.capacitysheetwidget.helpers;

import android.os.Handler;
import android.util.Log;

public class TimerThreadManager {

    private static final String TAG = "TimerThreadManager";
    private static final int DELAY_TIME_MILLIS = 2000;

    private static Handler timerHandler = new Handler();
    private static Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Executing Write operation!");
        }
    };

    public static void StartTimer(){
        ClearTimer();
        timerHandler.postDelayed(timerRunnable, DELAY_TIME_MILLIS);
    }

    private static void ClearTimer(){
        timerHandler.removeCallbacks(timerRunnable);
    }
}
