package com.choicely.imageservice.utils;

import java.io.Closeable;

/**
 * Collection of commonly used convenience methods.
 */
public class ChoicelyStaticUtils {

    public static void close(Closeable... closables) {
        for (Closeable c : closables) {
            try {
                c.close();
            } catch (Throwable ignore) {
            }
        }
    }

}
