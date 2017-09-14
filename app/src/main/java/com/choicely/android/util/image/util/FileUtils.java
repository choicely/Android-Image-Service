package com.choicely.android.util.image.util;

import android.support.annotation.Nullable;

import com.choicely.android.util.image.log.QLog;

import java.io.File;


/**
 * Created by Tommy
 */
public class FileUtils {

    private static final String TAG = "FileUtils";

    private static void deleteRecursive(File fileOrDirectory, Long lastModified) {
        if (fileOrDirectory == null) {
            return;
        }
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child, lastModified);
                }
            }
        }
        if (lastModified == null || fileOrDirectory.lastModified() < lastModified) {
            if (fileOrDirectory.delete()) {
                QLog.d(TAG, "File[%s] Delete success", fileOrDirectory.getAbsolutePath());
            } else {
                QLog.w(TAG, "File[%s] delete failed", fileOrDirectory.getAbsolutePath());
            }
        }
    }

    public static void deleteFiles(File file) {
        deleteFilesOlderThan(file, null);
    }

    public static void deleteFilesOlderThan(File directory, @Nullable Long timeModified) {
        if (directory == null || !directory.canRead() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        try {
            for (File child : files) {
                FileUtils.deleteRecursive(child, timeModified);
            }
        } catch (Exception e) {
            QLog.w(e, TAG, "Problem deleting children");
        }
    }

}
