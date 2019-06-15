# Capacity Sheet Widget

Capacity Sheet Widget is an Android home screen widget that streamlines interaction with a user-owned Capacity spreadsheet. A Capacity spreadsheet is a Google Sheets document that uses the **Capacity method** to track time-based activities and goals.

## Process

### widget init
1. MainActivity calls init asynctask.
1. MainActivity creates return intent, sets it to the widgetId, and finishes.
1. initasynctask gets and processes sheet data. makes UI decisions. 
1. in onPostExecute of initasynctask, it calls updateAppWidget to set the correct UI data


### widget update
1. onUpdate: widget creates the asynctask, give it references to its remoteViews, and executes
1. asynctask executes, and in its onPostExecute method it updates its remoteViews
1. asynctask calls updateAppWidget with the new remoteViews

## Backend

1. Used local keytool utility to generate an Android Studio debugging SHA1 fingerprint for my machine.
1. Created a Google Sheets API in the Google developer console, configured for that SHA1 fingerprint and the app's package name.
