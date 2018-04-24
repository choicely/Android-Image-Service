package com.choicely.imageservice;

import android.graphics.Bitmap;

/**
 * Listener interface for lazy calls to {@link ChoicelyImageService}.
 *
 * @author tommy
 */
public interface ImageServiceListener {

    /**
     * Called when image is ready for use.
     *
     * @param imageUrl     the image location string on the load that finished
     * @param image        the result image, or <code>null</code> if one could not be
     *                     retrieved
     * @param defaultResId defaultResId an id of a resource to show as a "loading" image.
     *                     Used to store the image location to view tag
     */
    void imageReady(final String imageUrl, final Bitmap image, final int defaultResId);

    void imageError(String imageUrl, int resultCode);

}
