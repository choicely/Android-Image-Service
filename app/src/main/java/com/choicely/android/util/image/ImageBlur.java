package com.choicely.android.util.image;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.IntRange;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;

import com.choicely.android.util.image.log.QLog;

public class ImageBlur {

    private static final String TAG = "ImageBlur";
    private static ScriptIntrinsicBlur script;
    private static RenderScript rs;

    public static Bitmap blur(Context context, Bitmap sentBitmap, @IntRange(from = 1, to = 25) int radius) {
        Bitmap bm;
        try {
            bm = internalBlur(context, sentBitmap, radius);
        } catch (Exception e) {
            QLog.w(e, TAG, "Error blurring image");
            bm = sentBitmap;
        }

        return bm;
    }

    private static boolean checkScript(Context context) {
        if (VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN_MR1) {
            return false;
        } else if (script != null) {
            return true;
        } else {
            rs = RenderScript.create(context);
            script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            return true;
        }
    }

    @SuppressLint("NewApi")
    public static Bitmap internalBlur(Context context, Bitmap sentBitmap, @IntRange(from = 1, to = 25) int radius) {

        if (checkScript(context)) {
            Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);

            Allocation input = Allocation.createFromBitmap(rs, sentBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            Allocation output = Allocation.createTyped(rs, input.getType());
            script.setRadius(radius);
            script.setInput(input);
            script.forEach(output);
            output.copyTo(bitmap);

            return bitmap;
        }
        QLog.w(TAG, "RenderScript not available");
        return sentBitmap;
    }

}
