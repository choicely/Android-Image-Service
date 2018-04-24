package com.choicely.imageservice.log;

import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * QuickLog
 * Convenience static service for logging and toggling log visibility.
 *
 * Created by Tommy on 03/05/15.
 */
public class QLog {

    private static boolean debug = true;

    private static final Set<QLogListener> listenerSet = new HashSet<>();

    public static void setDebug(boolean isDebugging) {
        debug = isDebugging;
    }

    public static boolean getDebug() {
        return debug;
    }

    public enum LogLevel {
        d, e, w, i, v
    }

    public static void addListener(QLogListener listener) {
        synchronized (listenerSet) {
            listenerSet.add(listener);
        }
    }

    public static void removeListener(QLogListener listener) {
        synchronized (listenerSet) {
            listenerSet.remove(listener);
        }
    }

    public static void notifyListeners(Throwable e, String tag, String message, LogLevel level, boolean debug) {
        synchronized (listenerSet) {
            for (QLogListener listener : listenerSet) {
                listener.onLog(e, tag, message, level, debug);
            }
        }
    }

    public static void d(String tag, String message, Object... args) {
        log(null, tag, message, LogLevel.d, debug, args);
    }

    public static void d(Throwable e, String tag, String message, Object... args) {
        log(e, tag, message, LogLevel.d, debug, args);
    }

    public static void i(String tag, String message, Object... args) {
        log(null, tag, message, LogLevel.i, debug, args);
    }

    public static void i(Throwable e, String tag, String message, Object... args) {
        log(e, tag, message, LogLevel.i, debug, args);
    }

    public static void w(String tag, String message, Object... args) {
        log(null, tag, message, LogLevel.w, debug, args);
    }

    public static void w(Throwable e, String tag, String message, Object... args) {
        log(e, tag, message, LogLevel.w, debug, args);
    }

    public static void e(String tag, String message, Object... args) {
        log(null, tag, message, LogLevel.e, debug, args);
    }

    public static void e(Throwable e, String tag, String message, Object... args) {
        log(e, tag, message, LogLevel.e, debug, args);
    }

    public static void log(Throwable e, String tag, String message, LogLevel level, boolean debug, Object... args) {
        if (!debug && level != LogLevel.e) {
            return;
        }
        message = String.format(message, args);
        notifyListeners(e, tag, message, level, debug);

        switch (level) {
            case v:
                Log.v(tag, message);
                break;
            case i:
                Log.i(tag, message);
                break;
            case w:
                if (e != null) {
                    Log.w(tag, message, e);
                } else {
                    Log.w(tag, message);
                }
                break;
            case e:
                if (e != null) {
                    Log.e(tag, message, e);
                } else {
                    Log.e(tag, message);
                }
                break;
            case d:
            default:
                if (e != null) {
                    Log.d(tag, message, e);
                } else {
                    Log.d(tag, message);
                }
                break;
        }
    }

}
