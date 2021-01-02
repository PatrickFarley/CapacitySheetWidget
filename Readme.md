# Capacity Sheet Widget

Capacity Sheet Widget is an Android home screen widget that streamlines interaction with a user-owned 
Capacity spreadsheet. A Capacity spreadsheet is a Google Sheets document that uses the **Capacity method** 
to track time-based activities and goals.

## Process

### widget view init
1. MainActivity collects entered sheet information, saves it to preferences on ContinueButtonClick.
1. MainActivity calls the init asynctask.
1. MainActivity creates a return intent, sets it to the widgetId, and finishes.
1. init asynctask gets and processes sheet data. Uses saved preferences to correctly make UI decisions. 
1. in onPostExecute of InitTask, it calls updateAppWidget to publish the correct UI data (the RemoteViews)

### Data entry
a global, trans-widget-ID switch for the different modes. When a data button is pressed, 

### widget view update
1. onUpdate: widget creates the asynctask, give it references to its remoteViews, and executes it
1. asynctask executes, and in its onPostExecute method it updates its remoteViews
1. asynctask calls updateAppWidget with the new remoteViews

## Backend
1. Used local keytool utility to generate an Android Studio debugging SHA1 fingerprint for my machine.
1. Created a Google Sheets API in the Google developer console, configured for that SHA1 fingerprint and the app's package name.
1. for publish, I will have to use a different SHA1 fingerprint? I forget.

# work context
first, get the onUpdate task working. for regular updates
Then you can start handling entry button clicks