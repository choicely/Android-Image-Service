package com.choicely.imageservice.log;

import android.text.TextUtils;

public class ChoicelyLogService {

    private boolean debug = true;
    private final int MAX_TAG_LENGTH = 20;
    protected String TAG;

    /**
     * Constructor, sets TAG name from Class Simple Name.
     */
    public ChoicelyLogService() {
        TAG = getClass().getSimpleName();
        if (TextUtils.isEmpty(TAG)) {
            Class superClass = getClass().getSuperclass();
            if (superClass != null) {
                TAG = superClass.getSimpleName();
            }
        }
        if (TAG != null && TAG.length() > MAX_TAG_LENGTH) {
            TAG = TAG.substring(0, MAX_TAG_LENGTH) + "...";
        }
    }

    public ChoicelyLogService(String tag) {
        TAG = tag;
        if (TAG != null && TAG.length() > MAX_TAG_LENGTH) {
            TAG = TAG.substring(0, MAX_TAG_LENGTH) + "...";
        }
    }

    /**
     * Set should service that extends UtilService log or not.
     *
     * @param isDebugging Is service debugging or not.
     */
    public void setDebug(boolean isDebugging) {
        debug = isDebugging;
    }

    public boolean getDebug() {
        return debug;
    }

    protected void v(String message, Object... args) {
        QLog.log(null, TAG, message, QLog.LogLevel.v, debug, args);
    }


    protected void v(Throwable t, String message, Object... args) {
        QLog.log(t, TAG, message, QLog.LogLevel.v, debug, args);
    }

    protected void i(String message, Object... args) {
        QLog.log(null, TAG, message, QLog.LogLevel.i, debug, args);
    }

    protected void i(Throwable t, String message, Object... args) {
        QLog.log(t, TAG, message, QLog.LogLevel.i, debug, args);
    }

    protected void d(String message, Object... args) {
        QLog.log(null, TAG, message, QLog.LogLevel.d, debug, args);
    }

    protected void d(Throwable t, String message, Object... args) {
        QLog.log(t, TAG, message, QLog.LogLevel.d, debug, args);
    }

    protected void w(String message, Object... args) {
        QLog.log(null, TAG, message, QLog.LogLevel.w, debug, args);
    }

    protected void w(Throwable t, String message, Object... args) {
        QLog.log(t, TAG, message, QLog.LogLevel.w, debug, args);
    }

    protected void e(String message, Object... args) {
        QLog.log(null, TAG, message, QLog.LogLevel.e, debug, args);
    }

    protected void e(Throwable t, String message, Object... args) {
        QLog.log(t, TAG, message, QLog.LogLevel.e, debug, args);
    }
}
