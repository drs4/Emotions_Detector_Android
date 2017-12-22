package com.s4u.emotionsdetector.http;

import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;

/**
 * Created by Saad on 2/18/2017.
 */

public class ProgressRequestBody extends RequestBody {

    private static final int SEGMENT_SIZE = 2048; // okio.Segment.SIZE

    private RequestBody requestBody;
    private GeneralCallback callback;
    private Handler handler;
    private File file;
    private long totalBytesRead = 0;


    public ProgressRequestBody(RequestBody requestBody,File file,Handler handler,GeneralCallback callback) {
        this.requestBody = requestBody;
        this.file = file;
        this.callback = callback;
        this.handler = handler;
    }

    @Override
    public MediaType contentType() {
        return requestBody.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return requestBody.contentLength();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {

        Source source = null;

        try {
            source = Okio.source(file);
            long read;
            totalBytesRead = 0;
            while ((read = source.read(sink.buffer(), SEGMENT_SIZE)) != -1) {
                totalBytesRead += read;
                sink.flush();
                if(callback!=null){
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onProgress((int)((100 * totalBytesRead) / file.length()));
                        }
                    });
                }

            }
        } finally {
            Util.closeQuietly(source);
        }

    }

}
