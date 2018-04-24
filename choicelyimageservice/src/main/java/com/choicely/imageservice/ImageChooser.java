package com.choicely.imageservice;

import android.graphics.Bitmap.Config;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.animation.Animation;
import android.widget.ImageView;

import com.choicely.imageservice.log.QLog;

import java.io.File;

/**
 * Created by Tommy on 03/01/2018.
 */
public class ImageChooser {

    private static final String TAG = "ImageInformation";

    public final String url;

    @Nullable
    File folder;
    @Nullable
    ImageServiceListener imageServiceListener;
    @Nullable
    ImageLoadListener loadListener;
    @Nullable
    Animation enterAnimation;
    @Nullable
    Animation exitAnimation;
    @Nullable
    Integer sampleSize = null;
    @Nullable
    Integer assignFailResource = null;

    @Nullable
    ImageModifier imageModifier;

    int defResource = R.color.cis_transparent;
    boolean isAssignedImmediately = false;

    boolean useDefaultResource = true;

    int blur = 0; // zero is no blur

    @NonNull
    Config bitmapConfig = Config.ARGB_8888;

    String thumbnailParentUrl = null;
    ImageChooser thumbnail = null;
    boolean isCrossFade = false;
    int crossFadeDuration = 200;

    public ImageChooser(String url) {
        this.url = url;
    }

    public ImageChooser(String url, int defResource, @Nullable Animation enterAnimation,
                        @Nullable Animation exitAnimation, @Nullable ImageServiceListener listener) {
        this.url = url;
        this.defResource = defResource;
        this.enterAnimation = enterAnimation;
        this.exitAnimation = exitAnimation;
        this.imageServiceListener = listener;
    }

    public ImageChooser setImageServiceListener(@Nullable ImageServiceListener imageServiceListener) {
        this.imageServiceListener = imageServiceListener;
        return this;
    }

    public ImageChooser setImageLoadListener(ImageLoadListener loadListener) {
        this.loadListener = loadListener;
        return this;
    }

    public ImageChooser setEnterAnimation(@Nullable Animation animation) {
        this.enterAnimation = animation;
        return this;
    }

    public ImageChooser setExitAnimation(@Nullable Animation animation) {
        this.exitAnimation = animation;
        return this;
    }

    public ImageChooser setAnimations(@Nullable Animation enterAnimation, @Nullable Animation exitAnimation) {
        this.enterAnimation = enterAnimation;
        this.exitAnimation = exitAnimation;
        return this;
    }

    public ImageChooser setDefaultResource(int defResource) {
        this.defResource = defResource;
        return this;
    }

    public ImageChooser useDefaultResource(boolean useDefaultResource) {
        this.useDefaultResource = useDefaultResource;
        return this;
    }

    public boolean isUseDefaultResource() {
        return useDefaultResource;
    }

    public ImageChooser sampleSize(Integer maxSize) {
        this.sampleSize = maxSize;
        return this;
    }

    public ImageChooser setFolder(@Nullable File folder) {
        this.folder = folder;
        return this;
    }

    public ImageChooser setBitmapConfig(@NonNull Config bitmapConfig) {
        this.bitmapConfig = bitmapConfig;

        return this;
    }

    public ImageChooser setAssignFailResource(@DrawableRes @Nullable Integer res) {
        this.assignFailResource = res;
        return this;
    }

    public ImageChooser setAssignImmediately(boolean assignImmediately) {
        this.isAssignedImmediately = assignImmediately;
        return this;
    }

    public boolean isAssignedImmediately() {
        return isAssignedImmediately;
    }

    public ImageChooser setImageModifier(@Nullable ImageModifier modifier) {
        this.imageModifier = modifier;
        if (thumbnail != null) {
            thumbnail.setImageModifier(modifier);
        }
        return this;
    }

    public boolean isBlur() {
        return blur > 0;
    }

    public ImageChooser setBlur(boolean isBlur) {
        this.blur = isBlur ? ChoicelyImageService.DEFAULT_BLUR : 0;
        return this;
    }

    public ImageChooser setBlur(@IntRange(from = 0, to = 25) int blur) {
        this.blur = blur;
        return this;
    }

    protected boolean isThumbnail() {
        return !TextUtils.isEmpty(thumbnailParentUrl);
    }

    protected boolean hasThumbnail() {
        return thumbnail != null;
    }

    public void setThumbnailUrl(String url) {
        setThumbnail(new ImageChooser(url));
    }

    public ImageChooser setThumbnail(ImageChooser thumbnail) {
        if (!TextUtils.isEmpty(this.thumbnailParentUrl)) {
            // note: thumbnail can not have thumbnail
            throw new IllegalStateException("Can not set thumbnail for a thumbnail!");
        }
        if (thumbnail != null && TextUtils.isEmpty(thumbnail.url)) {
            QLog.d(TAG, "not setting null thumbnail url");
            return this;
        }

        this.thumbnail = thumbnail;
        if (this.thumbnail != null) {
            this.thumbnail.thumbnailParentUrl = this.url;
            this.thumbnail.setAssignImmediately(true);
            if (imageModifier != null) {
                this.thumbnail.setImageModifier(imageModifier);
            }
        }

        return this;
    }

    public ImageChooser getThumbnail() {
        return thumbnail;
    }

    public ImageChooser setCrossFade(boolean crossFade) {
        isCrossFade = crossFade;
        return this;
    }

    public ImageChooser setCrossFadeDuration(int crossFadeDuration) {
        this.crossFadeDuration = crossFadeDuration;
        return this;
    }

    public static ImageChooser url(String url) {
        return new ImageChooser(url);
    }

    public void to(ImageView imageView) {
        ChoicelyImageService.getInstance().setImage(this, imageView);
    }

}
