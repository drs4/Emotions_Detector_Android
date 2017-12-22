package com.s4u.emotionsdetector.http;

import android.os.Handler;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

/**
 * Created by Saad on 2/15/2017.
 */

public class ProgressResponseBody extends ResponseBody {

    private ResponseBody responseBody;
    private BufferedSource bufferedSource;
    private GeneralCallback callback;
    private Handler handler;


    public ProgressResponseBody(ResponseBody responseBody,Handler handler, GeneralCallback callback)
    {
        this.responseBody = responseBody;
        this.handler = handler;
        this.callback = callback;
    }

    @Override public MediaType contentType() {
        return responseBody.contentType();
    }

    @Override public long contentLength() {
        return responseBody.contentLength();
    }

    @Override public BufferedSource source() {
        if (bufferedSource == null) {
            bufferedSource = Okio.buffer(source(responseBody.source()));
        }
        return bufferedSource;
    }

    private Source source(Source source) {
        return new ForwardingSource(source) {
            long totalBytesRead = 0L;

            @Override public long read(Buffer sink, long byteCount) throws IOException {
                long bytesRead = super.read(sink, byteCount);
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                if(callback!=null){
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onProgress((int)((100 * totalBytesRead) / responseBody.contentLength()));
                        }
                    });
                }
                return bytesRead;
            }
        };
    }
}