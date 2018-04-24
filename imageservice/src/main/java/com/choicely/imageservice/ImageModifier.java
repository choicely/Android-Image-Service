package com.choicely.imageservice;

import android.graphics.Bitmap;

/**
 * Created by Tommy on 02/11/15.
 */
public interface ImageModifier {

    Bitmap modify(Bitmap originalImage);

}
