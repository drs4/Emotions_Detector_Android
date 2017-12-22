package com.s4u.emotionsdetector.http;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * This class handles all Http requests, get, post and download
 * Created by Saad on 3/25/2016.
 */

public class HttpAPI{

    private OkHttpClient client;
    private Handler handler;
    private String baseURL;
    private Context context;
    private PreferenceCache preferenceCache;
    private GeneralCallback generalCallback;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType MEDIA_TYPE_MARKDOWN = MediaType.parse("text/x-markdown; charset=utf-8");

    public enum ErrorMessage{
        NO_INTERNET,
        SOCKET_TIMEOUT,
        UNKNOWN_HOST,
        UNKNOWN_ERROR
    }

    public enum NetworkState{
        UNKNOWN,
        WIFI,
        MOBILE,
        NOT_CONNECTED
    }

    /**
     * The main constructor of the class
     * @param context   base context of the app, it can take either activity or service
     * @param baseURL   base url, if set all requests will use it as prefix of the url given, it can be ""
     * @param timeout   timeout interval in seconds, if using other constructors it will take the value of 15sec
     * @param logging   if true, keep logging of all requests made by the client
     * @param trustAll  it true, the client will trust all ssl connections regardless of the certificate
     */
    public HttpAPI(Context context,String baseURL,int timeout, boolean logging,boolean trustAll)
    {

        this.baseURL = baseURL;
        this.context = context;

        int cacheSize = 10 * 1024 * 1024; // 10 MiB
        Cache cache = new Cache(new File("Cache.tmp"), cacheSize);// Real time cache, cleared after the app is closed

        OkHttpClient.Builder clientBuilder = trustAll? trustAllSSLBuilder() : new OkHttpClient.Builder();


        clientBuilder.cache(cache);

        clientBuilder.connectTimeout(timeout, TimeUnit.SECONDS);
        clientBuilder.readTimeout(timeout,TimeUnit.SECONDS);
        clientBuilder.writeTimeout(timeout,TimeUnit.SECONDS);

        //for download progress bar
        clientBuilder.addNetworkInterceptor(new Interceptor() {

            @Override
            public Response intercept(Chain chain) throws IOException {

                Response originalResponse = chain.proceed(chain.request());

                return originalResponse.newBuilder()
                        .body(new ProgressResponseBody(originalResponse.body(),handler,generalCallback))
                        .build();
            }
        });


        if(logging) clientBuilder.addInterceptor(new LoggingInterceptor());
        preferenceCache = new PreferenceCache(context,getClass().getSimpleName());

        client = clientBuilder.build();

        handler = new Handler(Looper.getMainLooper());
    }

    public HttpAPI(Context context,boolean logging)
    {
        this(context,"",15,logging,true);
    }

    public HttpAPI(Context context) {
        this(context,"",15,false,true);
    }

    //legacy upload code
    public void upload(String url,String data, File file) throws IOException {

        RequestBody body = RequestBody.create(JSON, data);

        RequestBody formBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(MediaType.parse("text/plain"), file))
                .addFormDataPart("filename", "name_of_the_file")

                .addFormDataPart("filetype", "cool")
                .addFormDataPart("filesize", "123")
                .addFormDataPart("receiver", "me")
                .addFormDataPart("sender", "he")
                .build();
        Request request = new Request.Builder().url(url).post(formBody).build();
        this.client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                log(response.body().string());
            }
        });

    }

    /**
     * Main method of the class, it is responsible for all requests.
     * @param url           this is abstract url, it don't take the base url in consideration. it should contain "http"
     * @param payload       String payload, if set to "" and the payloadFile to null, the request will be get
     * @param payloadFile   File payload, if set the string payload will be omitted, if null and string payload is empty the request will be get
     * @param path          the path of the file to save, if null the response will not be considered as a file
     * @param headers       hashmap of the headers, if null no headers will be set
     * @param caching       if true, the string response will be saved in the shared preferences. file responses will not be saved even if it can be parsed to string
     * @param callback      General callback to handle the response
     */
    private void baseAPI(final String url, String payload,File payloadFile, final String path, HashMap<String,String> headers, final boolean caching, final GeneralCallback callback)
    {
        log("URL: "+url);
        log("Payload: "+payload);
        log("Path: "+path);

        final String cached = caching ? preferenceCache.getStringPreference(url) : null;
        final HashMap<String,String> headersMap = new HashMap<>();

        if(caching && !TextUtils.isEmpty(cached))
        {
            callback.onCached(cached);
        }

        if(!isOnline())
        {
            callback.onNoInternet();
            callback.onProblem(ErrorMessage.NO_INTERNET, cached);
            return;
        }


        //extract the name of the output file from the url
        String[] tmp = url.split("/");
        String tmp1 = tmp[tmp.length - 1];
        String[] tmp3 = tmp1.split("-");
        final String name = tmp3[tmp3.length - 1];

        RequestBody body = RequestBody.create(JSON, payload);

        if(payloadFile != null)
        {

            byte[] b = new byte[(int) payloadFile.length()];
            try {
                FileInputStream fileInputStream = new FileInputStream(payloadFile);
                fileInputStream.read(b);
            } catch (Exception e) {
                System.out.println("File Not Found.");
                e.printStackTrace();
            }
            RequestBody requestBody =  RequestBody.create(MediaType.parse("application/octet-stream"), b);
            //for upload progress bar
            body = new ProgressRequestBody(requestBody,payloadFile,handler,callback);
        }


        Request.Builder builder = new Request.Builder();
        builder.url(url);
        //if the payload is empty it sends get request
        if(!TextUtils.isEmpty(payload) || payloadFile != null) builder.post(body);
        //add the headers
        if(headers != null)
        {
            for(String key:headers.keySet())
                builder.addHeader(key,headers.get(key));
        }

        Request request = builder.build();

        this.generalCallback = callback;


        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log(e.toString());

                if(e instanceof SocketTimeoutException)
                {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onTimeOut();
                            callback.onProblem(ErrorMessage.SOCKET_TIMEOUT,cached);
                        }
                    });
                }
                else if(e instanceof java.net.UnknownHostException)
                {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onUnknownHost();
                            callback.onProblem(ErrorMessage.UNKNOWN_HOST,cached);
                        }
                    });
                }
                else
                {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onProblem(ErrorMessage.UNKNOWN_ERROR,cached);
                        }
                    });
                }

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                if(!response.isSuccessful())
                {
                    log("Unexpected Code " + response.code());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onUnknownHost();
                            callback.onProblem(ErrorMessage.UNKNOWN_HOST,cached);
                        }
                    });
                }
                else
                {
                    Headers h = response.headers();
                    for(String s : h.names())
                    {
                        headersMap.put(s,h.get(s));
                    }

                    byte[] result = response.body().bytes();

                    if(path != null)
                    {
                        try
                        {
                            final File file = new File(path,name);
                            //Create the path if it didn't exist
                            File tmp = new File(path);
                            if(!file.exists())
                                tmp.mkdir();

                            OutputStream out = new FileOutputStream(file);
                            out.write(result);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onFinished(file,headersMap);
                                }
                            });
                        }
                        catch (Exception e)
                        {
                            log("Failed to write the file");
                        }

                    }

                    try
                    {

                        final String string = new String(result,"UTF-8");
                        if(caching && path==null) preferenceCache.setStringPreference(url,string);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onFinished(string,headersMap);
                            }
                        });

                    }
                    catch (Exception e)
                    {
                        log("Failed to parse to String");
                    }

                }

            }
        });

    }

    public void clearCache()
    {
        preferenceCache.clearPreference();
    }

    public void get(String url, final Callback callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {
                callback.onCached(response);
            }

            @Override
            public void onProgress(Integer percentage) {

            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage,cachedResponse);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(url,"",null,null,null,true, tmp);
    }


    public void get(String url, HashMap<String,String> headers, final Callback callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {
                callback.onCached(response);
            }

            @Override
            public void onProgress(Integer percentage) {

            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage,cachedResponse);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(baseURL + url, "",null, null,headers,true, tmp);
    }

    public void get(String url, final CallbackNoCache callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {

            }

            @Override
            public void onProgress(Integer percentage) {

            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(baseURL+url,"",null, null,null,false,tmp);
    }

    public void get(String url, HashMap<String,String> headers, final CallbackNoCache callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {

            }

            @Override
            public void onProgress(Integer percentage) {

            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(baseURL+url,"",null, null,headers,false,tmp);
    }


    public void post(String url, String payload, final Callback callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {
                callback.onCached(response);
            }

            @Override
            public void onProgress(Integer percentage) {

            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage,cachedResponse);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(baseURL + url, payload, null, null, null,true, tmp);
    }

    public void post(String url, String payload, HashMap<String,String> headers, final Callback callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {
                callback.onCached(response);
            }

            @Override
            public void onProgress(Integer percentage) {

            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage,cachedResponse);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(baseURL + url, payload, null, null, headers,true, tmp);
    }

    public void post(String url, String payload, final CallbackNoCache callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {

            }

            @Override
            public void onProgress(Integer percentage) {

            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(baseURL + url, payload, null, null, null,false, tmp);
    }

    public void post(String url, String payload, HashMap<String,String> headers, final CallbackNoCache callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {
            }

            @Override
            public void onProgress(Integer percentage) {

            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(baseURL + url, payload, null, null, headers,false, tmp);
    }


    public void postFile(String url,File file, final FileUploadCallback callback) {

        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {

            }

            @Override
            public void onProgress(Integer percentage) {
                callback.onProgress(percentage);
            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(baseURL + url,"",file,null,null,false,tmp);
    }

    public void postFile(String url,File file,HashMap<String,String> headers, final FileUploadCallback callback) {

        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {

            }

            @Override
            public void onProgress(Integer percentage) {
                callback.onProgress(percentage);
            }

            @Override
            public void onFinished(String response,HashMap<String,String> headers) {
                callback.onFinished(response,headers);
            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {

            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }
        };

        baseAPI(baseURL + url,"",file,null,headers,false,tmp);
    }

    public void downloadFile(String url, final String path,final FileDownloadCallback callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {

            }

            @Override
            public void onProgress(Integer percentage) {
                callback.onProgress(percentage);
            }

            @Override
            public void onFinished(String response, HashMap<String,String> headers) {

            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {
                callback.onFinished(file,headers);
            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }

        };

        baseAPI(baseURL + url, "", null, path, null,false, tmp);
    }

    public void downloadFile(String url, final String path,HashMap<String,String> headers,final FileDownloadCallback callback)
    {
        GeneralCallback tmp = new GeneralCallback() {
            @Override
            public void onCached(String response) {

            }

            @Override
            public void onProgress(Integer percentage) {
                callback.onProgress(percentage);
            }

            @Override
            public void onFinished(String response, HashMap<String,String> headers) {

            }

            @Override
            public void onProblem(ErrorMessage errorMessage,String cachedResponse) {
                callback.onProblem(errorMessage);
            }

            @Override
            public void onFinished(File file, HashMap<String,String> headers) {
                callback.onFinished(file,headers);
            }

            @Override
            public void onNoInternet() {

            }

            @Override
            public void onUnknownHost() {

            }

            @Override
            public void onTimeOut() {

            }

        };

        baseAPI(baseURL + url, "", null, path, headers,false, tmp);
    }

    public NetworkState getNetState() {
        NetworkState state = null;
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        state = NetworkState.UNKNOWN;
        NetworkInfo activeInfo = connMgr.getActiveNetworkInfo();
        if (activeInfo != null && activeInfo.isConnected()) {
            if(activeInfo.getType() == ConnectivityManager.TYPE_WIFI)
                state = NetworkState.WIFI;
            if(activeInfo.getType() == ConnectivityManager.TYPE_MOBILE)
                state = NetworkState.MOBILE;
        } else {
            state = NetworkState.NOT_CONNECTED;
        }
        return state;
    }

    public boolean isOnline()
    {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeInfo = connMgr.getActiveNetworkInfo();

        return activeInfo != null && activeInfo.isConnected();
    }

    private void log(String log)
    {
        Log.d("Debug",getClass().getSimpleName()+": "+log);
    }

    private static OkHttpClient.Builder trustAllSSLBuilder() {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        clientBuilder.hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });
        X509TrustManager trustManager = new X509TrustManager() {

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[] {};
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1)
                    throws CertificateException {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1)
                    throws CertificateException {
            }
        };

        TrustManager[] trustAllCerts = new TrustManager[] {trustManager};

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            clientBuilder.sslSocketFactory(sc.getSocketFactory(),trustManager);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return clientBuilder;
    }

}
