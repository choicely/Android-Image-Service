package com.choicely.android.util.image.web;

import java.io.File;

/**
 * Created by Tommy
 */
public interface FileDownloader {

    void downloadFile(String url, File file, FileDownloadListener listener);

}
