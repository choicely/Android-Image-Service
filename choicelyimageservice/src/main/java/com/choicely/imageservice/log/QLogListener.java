package com.choicely.imageservice.log;


import com.choicely.imageservice.log.QLog.LogLevel;

/**
 * Created by Tommy on 07/09/16.
 */

public interface QLogListener {

    void onLog(Throwable e, String tag, String message, LogLevel level, boolean debug);

}
