# BikePulse - Strava Bike Dashboard for Wear OS

A modern and sleek Wear OS application designed for cyclists to track their weekly and yearly Strava performance directly from their wrist.

## 🎯 The Motivation

The native Strava app for Wear OS is excellent for recording activities, but it lacks a comprehensive **Dashboard**. Currently, it only provides controls for starting, pausing, and ending an exercise. 

**BikePulse** fills this gap by providing a dedicated visual summary of your progress. Furthermore, for users with **LTE (4G) enabled smartwatches**, this app becomes even more powerful: as soon as you complete and sync your exercise on the native Strava app, you can open BikePulse to see your updated weekly and yearly stats instantly, anywhere, without needing your phone.

## 🚲 Features

- **Summary Dashboard**: View total distance (KM) and time for the current week.
- **Detailed Activity List**: Scroll through your recent cycling activities with auto-scroll to top on data load.
- **Yearly Statistics**: Track your progress over the years with cards showing activity count, distance, elevation, and total time.
- **Smart Login Flow**: Automatic detection of authentication status. If a token is missing, it triggers the authorization flow on your phone instantly.
- **Premium UI**: 
    - Dark theme with elegant blue gradient cards (#0089A1 to #003747).
    - Custom-designed launcher icon with a double-ring (White & Deep Blue) and centered bicycle.
    - Synchronized Splash Screen for a seamless app entry.
- **English Localization**: Fully translated to English (US) with standard date formatting ("FRI, 17 APR").

## 🛠️ Setup & Requirements

### 1. Strava API Credentials
To protect sensitive data, this project uses `local.properties` to manage API keys.
1. Go to the [Strava API Dashboard](https://www.strava.com/settings/api).
2. Create your application.
3. Set the **Authorization Callback Domain** to: `wear.googleapis.com`

### 2. Local Configuration
Create or edit the `local.properties` file in the root directory of the project and add your credentials:

```properties
STRAVA_CLIENT_ID=YOUR_CLIENT_ID_HERE
STRAVA_CLIENT_SECRET=YOUR_CLIENT_SECRET_HERE
BIKE_MODEL_NAME=Your Bike Model Name
```

### 3. Build & Run
1. Open the project in **Android Studio**.
2. Perform a **Gradle Sync** to ensure the `BuildConfig` is generated with your keys.
3. Deploy the `:wear` module to your Wear OS device or emulator.

## ⚠️ Disclaimer

**This is a personal integration project.** 
- This application is not affiliated with, endorsed by, or sponsored by Strava, Inc.
- It uses the Strava API according to their terms of service, but you are responsible for your own API usage limits and data privacy.
- The branding "BikePulse" is a custom identifier used for this specific project iteration.

## 🎨 Visual Identity
- **Primary Color**: Bike Blue (`#00E5FF`)
- **Accent Color**: Bike Green (`#00FF88`)
- **Background**: Solid Black (`#000000`)
- **Cards**: Linear Gradient from `#0089A1` to `#003747`.

