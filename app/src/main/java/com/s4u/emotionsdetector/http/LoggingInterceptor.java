package com.s4u.emotionsdetector.http;

import android.util.Log;

import java.io.IOException;
import java.util.logging.Logger;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Saad on 4/12/2016.
 */
public class LoggingInterceptor implements Interceptor {
    private static String TAG = "Debug:LoggingInterceptor";
    private static final Logger logger = Logger.getLogger(HttpAPI.class.getName());

    @Override
    public Response intercept(Chain chain) throws IOException {
        long t1 = System.nanoTime();
        Request request = chain.request();
        logger.info(String.format("Sending request %s on %s%n%s",
                request.url(), chain.connection(), request.headers()));
        Log.d(TAG, String.format("Sending request %s on %s%n%s",
                request.url(), chain.connection(), request.headers()));
        Response response = chain.proceed(request);

        long t2 = System.nanoTime();
        logger.info(String.format("Received response for %s in %.1fms%n%s",
                request.url(), (t2 - t1) / 1e6d, response.headers()));
        Log.d(TAG,String.format("Received response for %s in %.1fms%n%s",
                request.url(), (t2 - t1) / 1e6d, response.headers()));
        return response;
    }
}
