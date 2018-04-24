package com.choicely.imageservice.web;

import com.choicely.imageservice.utils.ChoicelyStaticUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by Tommy on 3/5/2015.
 */
public class OkFileProgressHandler extends OkResponseHandler<File> {

    private File file;

    public OkFileProgressHandler(File file) {
        super("OkFileProgressHandler");
        this.file = file;
        setDebug(false);
    }

    public File handleResponse(Response response) {
        InputStream is = null;
        BufferedInputStream input = null;
        OutputStream output = null;
        try {
            HttpUrl url = response.request().url();
            ResponseBody body = response.body();
            is = body.byteStream();
            input = new BufferedInputStream(is);
            output = new FileOutputStream(file);

            byte[] data = new byte[1024];

            int count;
            float max = body.contentLength();
            float totalLoad = 0f;
            onProgress(0f);
            while ((count = input.read(data)) != -1) {
                totalLoad += count;
                onProgress(totalLoad / max);
                output.write(data, 0, count);
            }
            onProgress(1f);
            d("[%s]File[%s] handled", url, file.getAbsolutePath());
            output.flush();
        } catch (IOException e) {
            w(e, "Error loading file");
        }
        ChoicelyStaticUtils.close(is, input, output, response);

        return file;
    }

    @Override
    public void closeOpenResources() {
        // nothing needs to be closed
    }

    /**
     * Progress change handling method. Super implementation only logs so it can be ignored safely.
     *
     * @param progress Float progress between [0, 1].
     */
    public void onProgress(float progress) {
        d("onProgress[%s]", Float.toString(progress));
    }

}
