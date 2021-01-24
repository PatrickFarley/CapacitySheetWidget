package com.patrickdfarley.capacitysheetwidget;

import android.appwidget.AppWidgetManager;
import android.net.Uri;
import android.os.Bundle;

import com.google.api.services.sheets.v4.SheetsScopes;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;

import com.google.api.client.util.ExponentialBackOff;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.concurrent.Executors;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

// MainActivity handles the initial data entry for locating the Google sheet.
public class MainActivity extends Activity {
    GoogleAccountCredential mCredential;
    GoogleAccountCredential tempCredential;
    TextView mOutputText;
    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static public final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    static final String MYPREFERENCES = "MyPrefs";
    private static final String TAG = "MainActivity";
    private static final String PREF_ACCOUNT_NAME = "Capacity Sheet Account Name";
    private static final String[] SCOPES = { SheetsScopes.SPREADSHEETS_READONLY, SheetsScopes.SPREADSHEETS };

    SharedPreferences sharedpreferences;


    // This activity is initially created by the widget itself (called from its manifest/appwieget_info)
    // This seems to call the widgetmanager's OnUpdate when this method completes.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate called");

        mOutputText = findViewById(R.id.sampleTextView);
        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Sheets API ...");

        sharedpreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // here we're immediately setting the initialized flag to false. Won't be set true until
        // onContinueButtonClicked gets run.
        SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.putBoolean("IsInitialized", false);
        editor.apply();

        setContentView(R.layout.activity_main);

        // Initialize credentials and service object. GoogleAccountCredential is a thread-safe
        // helper class for OAuth 2.0 for accessing protected resources using an access token.
        mCredential = null;
        tempCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        createCredential();
    }

    public void onContinueButtonClicked(View view){
        Log.d(TAG,"ContinueButton clicked");
        // check inputs, handle blank fields
        if (((EditText)findViewById(R.id.spreadsheetIdEditText)).getText().equals("") ||
                ((EditText)findViewById(R.id.sheetNameEditText)).getText().equals("") ||
                ((EditText)findViewById(R.id.dataRangeEditText)).getText().equals("")){
            Toast toast = Toast.makeText(this, "Enter a value in each field",Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        // store entered form data in shared preferences
        SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.putString("SpreadsheetId",((EditText)findViewById(R.id.spreadsheetIdEditText)).getText().toString());
        editor.putString("SheetName",((EditText)findViewById(R.id.sheetNameEditText)).getText().toString());
        editor.putString("DataRange",((EditText)findViewById(R.id.dataRangeEditText)).getText().toString());
        editor.putBoolean("IsInitialized", true);
        editor.apply();

        // update the appwidget views with default values
        // TODO: Is this line necessary? This is (if successful) done by the following AsyncTask
        // appWidgetManager.updateAppWidget(appWidgetId, views);

        if (mCredential != null){
            // get info needed to interact with the widget, including a default RemoteViews of the
            // widget's layout
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            RemoteViews newView = new RemoteViews(this.getPackageName(),
                    R.layout.capacity_appwidget);
            // get ID info on the widget that launched this activity, so we can return to it.
            // The ID is contained in the intent that launched this activity.
            Intent intent = getIntent();
            int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
            Bundle extras = intent.getExtras();
            if (extras != null) {
                appWidgetId = extras.getInt(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID);
            }

            // update UI:
            final UIManager uIManager = new UIManager(this, appWidgetId, appWidgetManager, newView);
            final Handler mainThreadHandler = new Handler(Looper.getMainLooper()); // TODO: this is a deprecated method; might want to update the Android version
            final Context contextCopy = this;
            Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    SheetsCallManager sheetsCallManager = new SheetsCallManager(mCredential, contextCopy);
                    sheetsCallManager.saveMetaDataToPrefs();

                    mainThreadHandler.post(new Runnable(){
                        @Override
                        public void run() {
                            uIManager.updateUI();
                        }
                    });
                }
            });


            // and finish this activity, creating an intent to return.
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();

        } else {
            Log.d(TAG, "The credential is not yet created.");
            //TODO: should the activity finish in this case, or stay open?
        }

    }


    //region Credential provider

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the method will exit and the
     * app will prompt the user as
     * appropriate.
     */
    private void createCredential() {
        Log.d(TAG, "createCredential called");
        //checkAccounts();

        if (! isGooglePlayServicesAvailable()) {
            Log.d(TAG, "CC: Google play services was not available. Calling acquireGooglePlayServices()...");
            acquireGooglePlayServices();
        } else if (tempCredential.getSelectedAccountName() == null) {
            Log.d(TAG, "CC: No selected account. Calling chooseAccount()");
            chooseAccount();
        } else if (! isDeviceOnline()) {
            Log.d(TAG, "CC: No network connection available.");
            mOutputText.setText("No network connection available.");
        } else {
            mCredential = tempCredential;
            Log.d(TAG, "CC: Credential is ready: "+ mCredential.toString());
        }
    }

//    public void checkAccounts(){
//        String ans = isGoogleAccountPresent();
//        Log.d(TAG,"google accounts?: " + ans);
//    }

//    public String isGoogleAccountPresent() {
//
//        String toReturn = "";
//        AccountManager manager = AccountManager.get(this);
//        for(Account account : manager.getAccounts()) {
//            toReturn += account.toString() + " ";
//        }
//        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)){
//            toReturn += " has permission";
//        } else {
//            toReturn += " does not have permission";
//        }
//        return toReturn;
//    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS, Manifest.permission.READ_CONTACTS)) {

            String accountName = PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_ACCOUNT_NAME, null);
            // if an account was stored in the preferences
            if (accountName != null) {

//                GoogleAccountManager mgr = new GoogleAccountManager(this);
//                Account[] accountList = mgr.getAccounts();
                tempCredential.setSelectedAccountName(accountName);
                createCredential();
            } else {
                // when we don't have an account stored in preferences
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        tempCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            // This code runs the first time the app is installed.
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS, Manifest.permission.READ_CONTACTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with.
     * The resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    createCredential();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        // add to preferences
                        SharedPreferences settings =
                                PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();

                        //mCredential.setSelectedAccountName(accountName);
                        createCredential();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    createCredential();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

//    /**
//     * Callback for when a permission is granted using the EasyPermissions
//     * library.
//     * @param requestCode The request code associated with the requested
//     *         permission
//     * @param list The requested permission list. Never null.
//     */
//    @Override
//    public void onPermissionsGranted(int requestCode, List<String> list) {
//        // Do nothing.
//    }
//
//    /**
//     * Callback for when a permission is denied using the EasyPermissions
//     * library.
//     * @param requestCode The request code associated with the requested
//     *         permission
//     * @param list The requested permission list. Never null.
//     */
//    @Override
//    public void onPermissionsDenied(int requestCode, List<String> list) {
//        // Do nothing.
//    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }
    //endregion

}
