package com.patrickdfarley.capacitysheetwidget.helpers;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Timer;

/*
This is a singleton class: static method returns an instance of it. It manages the EntryAmount countdown timer.
 */
public class TimerThreadManager {

    private final String TAG = "TimerThreadManager";
    private final int DELAY_TIME_MILLIS = 2000;
    private Handler timerHandler;

    private static TimerThreadManager instance = null;

    private TimerThreadManager(){
        timerHandler = new Handler();
    }

    public static TimerThreadManager getInstance(){
        if (instance ==null){
            instance = new TimerThreadManager();
        }
        return instance;
    }


    public void RestartTimer(Runnable timerRunnable){
        Log.d(TAG, "RestartTimer called");
        ClearTimer();
        //timerHandler.post(timerRunnable);
        timerHandler.postDelayed(timerRunnable, DELAY_TIME_MILLIS);
    }

    private void ClearTimer(){
        timerHandler.removeCallbacksAndMessages(null);
    }
}
