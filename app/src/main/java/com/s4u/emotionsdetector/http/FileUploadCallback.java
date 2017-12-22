package com.s4u.emotionsdetector.http;

import java.util.HashMap;

/**
 * Created by Saad on 2/18/2017.
 */

public interface FileUploadCallback {
    void onProgress(Integer percentage);
    void onFinished(String response, HashMap<String, String> headers);
    void onProblem(HttpAPI.ErrorMessage errorMessage);
}
