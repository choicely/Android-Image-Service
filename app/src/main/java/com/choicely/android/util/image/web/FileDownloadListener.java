package com.choicely.android.util.image.web;

import android.support.annotation.FloatRange;

import java.io.File;

/**
 * Created by Tommy
 */
public interface FileDownloadListener {

    void onSuccess(int statusCode, File file);

    void onError(int statusCode);

    void onProgress(@FloatRange(from = 0f, to = 1f) float progress);

}
