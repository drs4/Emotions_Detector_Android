package com.s4u.emotionsdetector.http;


import java.util.HashMap;

/**
 * Created by Saad on 11/18/2016.
 */
public interface CallbackNoCache
{
    void onFinished(String response, HashMap<String, String> headers);
    void onProblem(HttpAPI.ErrorMessage errorMessage);
}
