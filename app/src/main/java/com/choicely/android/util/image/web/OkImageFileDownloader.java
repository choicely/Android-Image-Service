package com.choicely.android.util.image.web;

import com.choicely.android.util.image.log.LogService;
import com.choicely.android.util.image.util.ImageUtils;

import java.io.File;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Tommy
 */
public class OkImageFileDownloader extends LogService implements FileDownloader {

    private static final long READ_TIMEOUT = 30;
    private OkHttpClient web;

    public OkImageFileDownloader() {
        setDebug(false);
        web = new OkHttpClient.Builder()
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .build();
    }

    public OkImageFileDownloader(boolean debug) {
        setDebug(debug);
        web = new OkHttpClient.Builder()
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void downloadFile(final String url, File file, final FileDownloadListener listener) {
        Response response;
        Request.Builder requestBuilder = new Request.Builder();
        Request r = requestBuilder.url(url).get().build();
        try {
            response = web.newCall(r).execute();
        } catch (Exception e) {
            w(e, "Problem performing request[%s]", url);
            response = null;
        }
        File resultFile;
        int statusCode;
        if (response != null && response.isSuccessful()) {
            // Handle success
            statusCode = response.code();
            resultFile = new OkFileProgressHandler(file) {

                @Override
                public void onProgress(float progress) {
//                    super.onProgress(progress);
                    d("[%s]LoadingProgress[%s]", url, Float.toString(progress));
                    if (listener != null) {
                        listener.onProgress(progress);
                    }
                }

            }.handleResponse(response);

            if (listener != null) {
                listener.onSuccess(statusCode, resultFile);
            }
        } else {
            // Handle error
            statusCode = -1;
            if (response != null) {
                statusCode = response.code();
                ImageUtils.close(response.body());
            }
            if (listener != null) {
                listener.onError(statusCode);
            }
        }

    }
}
