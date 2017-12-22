package com.s4u.emotionsdetector.http;


import java.io.File;
import java.util.HashMap;

/**
 * Created by Saad on 11/18/2016.
 */
public interface GeneralCallback
{
    void onCached(String response);
    void onProgress(Integer percentage);
    void onFinished(String response, HashMap<String, String> headers);
    void onProblem(HttpAPI.ErrorMessage errorMessage, String cachedResponse);
    void onFinished(File file, HashMap<String, String> headers);
    void onNoInternet();
    void onUnknownHost();
    void onTimeOut();
}

