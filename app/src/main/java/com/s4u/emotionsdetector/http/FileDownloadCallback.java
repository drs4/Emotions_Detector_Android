package com.s4u.emotionsdetector.http;


import java.io.File;
import java.util.HashMap;

/**
 * Created by Saad on 11/18/2016.
 */
public interface FileDownloadCallback
{
    void onProgress(Integer percentage);
    void onFinished(File file, HashMap<String, String> headers);
    void onProblem(HttpAPI.ErrorMessage errorMessage);
}
