package com.ben.drivenbluetooth.threads;


import android.os.Looper;
import android.util.Log;

import com.ben.drivenbluetooth.Global;
import com.ben.drivenbluetooth.events.DialogEvent;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

//import java.util.Iterator;

public class TelemetrySender extends Thread {
private boolean telEnabled;
private Timer telUpdateTimer = new Timer();
private String echookID = "";
private boolean waitingForLogin = false;
private int currentLap = 0;
private boolean firstRun = true;



public TelemetrySender() {
        telEnabled = (Global.dweetEnabled || Global.eChookLiveEnabled || Global.userDefinedURLEnabled);
        //Log.d("eChook","TelemetrySender: telEnabled = " + telEnabled );
}


@Override
public void run() {
        Looper.prepare();
        telUpdateTimer.schedule(sendJsonTask, 0, 1500);

        boolean echookIdReceived;

        if (telEnabled && Global.eChookLiveEnabled && (!Global.eChookCarName.equals("")) && !Global.eChookPassword.equals("")) {     //If echook live is selected and login details aren't empty
                Log.d("eChook", "Attempting to get eChook Live ID");
                echookIdReceived = getEchookId();
                if(!echookIdReceived) {
                    EventBus.getDefault().post(new DialogEvent("eChook Login Failed", ""));
                }
                waitingForLogin = false;
        }

        Looper.loop();
}

private TimerTask sendJsonTask = new TimerTask(){
        @Override
        public void run() {
                if (telEnabled) {
                                Log.d("eChook", "About to trigger JSON Data");
                                Boolean success = sendJSONData();
                }
        }
};

private boolean getEchookId()
{
        Log.d("eChook", "Getting eChook Live Login");
        boolean success;
        HttpURLConnection urlConnection;
        try {
                URL url;

                url = new URL("https://data.echook.uk/api/getid");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setChunkedStreamingMode(0);
                urlConnection.setRequestProperty("content-type","application/json");

                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(getLoginJson().toString().getBytes());
                out.flush();


                StringBuilder sb = new StringBuilder();
                int HttpResult = urlConnection.getResponseCode();
                if (HttpResult == HttpURLConnection.HTTP_OK) {
                        Log.d("eChook", "HTTP Response OK to Login Request");
                        BufferedReader br = new BufferedReader(
                                new InputStreamReader(urlConnection.getInputStream(), "utf-8"));
                        String line;
                        while ((line = br.readLine()) != null) {
                                sb.append(line).append("\n");
                        }
                        br.close();

                        Log.d("eChook", "ID request response:"+sb.toString());

                        try {
                                JSONObject receivedJson = new JSONObject(sb.toString());
                                echookID = receivedJson.getString("id");
                        }catch (JSONException e)
                        {
                                e.printStackTrace();
                                Log.d("eChook", "ID not Found - JSON exception");
                        }


                        success = true;

                        Log.d("eChook", "Received ID:"+ echookID);
                        EventBus.getDefault().post(new DialogEvent("eChook Login Successful", ""));


                } else {
                        System.out.println(urlConnection.getResponseMessage());
                        success = false;
                        EventBus.getDefault().post(new DialogEvent("eChook Login Failed", "Bad Response from Server"));
                        Log.d("eChook", "Non OK response to eChook Live login request");
                }
                urlConnection.disconnect();

        }catch (IOException e) {
                e.printStackTrace();
                success = false;
        }

        return success;
}


private JSONObject getLoginJson()
{
        JSONObject loginJson = new JSONObject();
        try{
                loginJson.put("username", Global.eChookCarName);
                loginJson.put("password", Global.eChookPassword);

        }catch (JSONException e) {
                e.printStackTrace();
        }

        return loginJson;

}

private JSONObject getDataJson(boolean location, boolean lapData, boolean customData)
{
        JSONObject dataJSON = new JSONObject();
        DecimalFormat format2 = new DecimalFormat("#.##");
        DecimalFormat format3 = new DecimalFormat("#.###");
        try{
                dataJSON.put("Vt", format2.format(Global.Volts));
                dataJSON.put("V1", format2.format(Global.VoltsAux));
                dataJSON.put("A", format2.format(Global.Amps));
                dataJSON.put("RPM", format2.format(Global.MotorRPM));
                dataJSON.put("Spd", format2.format(Global.SpeedMPS));
                dataJSON.put("Thrtl", format2.format(Global.InputThrottle));
                dataJSON.put("AH", format2.format(Global.AmpHours));
                dataJSON.put("Lap", format2.format(Global.Lap));
                dataJSON.put("Tmp1", format2.format(Global.TempC1));
                dataJSON.put("Tmp2", format2.format(Global.TempC2));
                dataJSON.put("Brk", format2.format(Global.Brake));
                dataJSON.put("Gear", format2.format(Global.Gear));
                dataJSON.put("Distance", format2.format(Global.DistanceMeters));
                dataJSON.put("WattHoursPerMeter", format2.format(Global.WattHoursPerMeter));

                if (customData) {
                    dataJSON.put("Custom0", format3.format(Global.Custom0));
                    dataJSON.put("Custom1", format3.format(Global.Custom1));
                    dataJSON.put("Custom2", format3.format(Global.Custom2));
                    dataJSON.put("Custom3", format3.format(Global.Custom3));
                    dataJSON.put("Custom4", format3.format(Global.Custom4));
                    dataJSON.put("Custom5", format3.format(Global.Custom5));
                    dataJSON.put("Custom6", format3.format(Global.Custom6));
                    dataJSON.put("Custom7", format3.format(Global.Custom7));
                    dataJSON.put("Custom8", format3.format(Global.Custom8));
                    dataJSON.put("Custom9", format3.format(Global.Custom9));
                }

                if(location) {
                        dataJSON.put("Lat", Global.Latitude);
                        dataJSON.put("Lon", Global.Longitude);
                }

                if(currentLap != Global.Lap && Global.Lap > 0 && lapData) {
                        currentLap = Global.Lap;
                        dataJSON.put("LL_V", format2.format(Global.LapDataList.get(currentLap-1).getAverageVolts()));
                        dataJSON.put("LL_I", format2.format(Global.LapDataList.get(currentLap-1).getAverageAmps()));
                        dataJSON.put("LL_RPM", format2.format(Global.LapDataList.get(currentLap-1).getAverageRPM()));
                        dataJSON.put("LL_Spd", format2.format(Global.LapDataList.get(currentLap-1).getAverageSpeedMPS()));
                        dataJSON.put("LL_Ah", format2.format(Global.LapDataList.get(currentLap-1).getAmpHours()));
                        dataJSON.put("LL_Time", format2.format(Global.LapDataList.get(currentLap-1).getLapTimeString()));
                        dataJSON.put("LL_Eff", format2.format(Global.LapDataList.get(currentLap-1).getWattHoursPerKM()));
                }

                if(firstRun & lapData) {
                        firstRun = false;
                        dataJSON.put("LL_V", "0");
                        dataJSON.put("LL_I", "0");
                        dataJSON.put("LL_RPM", "0");
                        dataJSON.put("LL_Spd", "0");
                        dataJSON.put("LL_Ah", "0");
                        dataJSON.put("LL_Time", "0");
                        dataJSON.put("LL_Eff", "0");
                }

        }catch (JSONException e) {
                e.printStackTrace();
        }

        return dataJSON;
}

private boolean HttpSend(String urlString, String method, String username, String password, String requestPropertyKey, String requestPropertyValue, String json) {
    try {
        //urlString = "http://192.168.1.43:45455/api/test";       // TESTING

        URL url = new URL(urlString);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        if (method != null) {
            urlConnection.setRequestMethod(method);
        }
        if ((username != null) && !(username.equals("")) && (password != null)) {
            String userpass = username + ":" + password;
            String header = "Basic " + new String(android.util.Base64.encode(userpass.getBytes(), android.util.Base64.NO_WRAP));
            urlConnection.addRequestProperty("Authorization", header);
        }
        urlConnection.setDoOutput(true);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        urlConnection.setFixedLengthStreamingMode(jsonBytes.length);
        urlConnection.setRequestProperty("Content-Type", "application/json");
        if (requestPropertyKey != null) {
            urlConnection.setRequestProperty(requestPropertyKey, requestPropertyValue);
        }

        OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.flush();

        StringBuilder sb = new StringBuilder();
        int HttpResult = urlConnection.getResponseCode();
        if (HttpResult == HttpURLConnection.HTTP_OK) {
            Log.d("SendData", "HTTP Response OK");
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(urlConnection.getInputStream(), "utf-8"));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();
            System.out.println("" + sb.toString());
        } else {
            System.out.println(urlConnection.getResponseMessage());
        }

        urlConnection.disconnect();

    } catch (IOException e) {
        e.printStackTrace();
        return false;
    }

    return true;
}

private boolean sendJSONData() {

        boolean success = true;

        if(Global.dweetEnabled) {
                Log.d("eChook", "Entering Send Dweet Data");

                boolean b = HttpSend("https://dweet.io/dweet/for/" + Global.dweetThingName,
                                null, null, null,null, null,
                                 getDataJson(false, true, true).toString());

                success = success && b;
        }

        if(Global.eChookLiveEnabled) {
                Log.d("eChook", "Entering eChook Live SendJSON data, ID = " + echookID);
                if (Global.eChookLiveEnabled && !waitingForLogin && echookID.equals("")) //Catches the usecase when someone enables eChook live data once the thread is started and a login is needed.
                {
                        Log.d("eChook", "eChook Live enabled but no ID. ID = " + echookID);
                        waitingForLogin = true;
                        getEchookId();
                        return true;
                } else {

                        boolean b =  HttpSend("https://data.echook.uk/api/send/" + echookID,
                                null, null, null,"X-DWEET-AUTH", echookID,
                                getDataJson(true, true, true).toString());

                        success = success && b;
                }
        }

        if(Global.userDefinedURLEnabled && Global.userDefinedURL != null && (!Global.userDefinedURL.equals(""))) {
                Log.d("eChook", "Entering SendJSON data to user defined URL");

                JSONObject dataJSON = getDataJson(true, false, true);
                try {
                        dataJSON.put("Timestamp", Long.toString(System.currentTimeMillis()));
                        dataJSON.put("CarName", Global.userDefinedURLCarName);
                        dataJSON.put("Type", "Raw");
                }
                catch (JSONException e) {
                        e.printStackTrace();
                }

                boolean b = HttpSend(Global.userDefinedURL,
                        "POST", Global.userDefinedURLUsername, Global.userDefinedURLPassword,null, null,
                        dataJSON.toString());

                success = success && b;
        }

        return success;
}


public void Enable() {
        telEnabled = true;
}

public void Disable() {
        telEnabled = false;
}

public void pause() {
        telEnabled = false;
}

public void restart() {
        telEnabled = Global.dweetEnabled || Global.eChookLiveEnabled  || Global.userDefinedURLEnabled;

}
}
