package com.choicely.android.util.image;

import android.graphics.Bitmap;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;

import com.choicely.android.util.image.log.LogService;

/**
 * Created by Tommy
 */
public class OnAnimationEnd extends LogService implements AnimationListener {

    private ImageView view;
    private Bitmap image;
    private ImageInfo info;

    public OnAnimationEnd(Bitmap image, ImageView view, ImageInfo info) {
        this.image = image;
        this.view = view;
        this.info = info;

        setDebug(ImageService.getInstance().getDebug());
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        if (view != null && info.enterAnimation != null) {
            String tag = (String) view.getTag(R.id.cis_tag_id);
            if (tag != null && !info.url.equals(tag)) {
                d("Different image already queued");
            } else {
                d("Enter animation started");
                view.setImageBitmap(image);
                view.startAnimation(info.enterAnimation);
            }
        }
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
    }

    @Override
    public void onAnimationStart(Animation animation) {
    }

}
