package com.choicely.android.util.image;

import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.animation.Animation;
import android.widget.ImageView;

import com.choicely.android.util.image.log.QLog;
import com.choicely.android.util.image.web.FileDownloadListener;

import java.io.File;


public class ImageInfo {

    private static final String TAG = "ImageInfo";

    public final String url;

    public static final int DEFAULT_BLUR = 3;

    @Nullable
    File folder;
    @Nullable
    ImageServiceListener imageServiceListener;
    @Nullable
    FileDownloadListener loadListener;
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

    CompressFormat compressFormat = CompressFormat.JPEG;
    int compression = ImageService.DEFAULT_COMPRESSION;
    int defResource = R.color.cis_default_image_resource;
    boolean isHighQualityScaling = false;
    boolean isAssignedImmediately = false;

    boolean useDefaultResource = true;

    @IntRange(from = 0, to = 25)
    int blur = 0;

    @NonNull
    Config bitmapConfig = Config.ARGB_8888;

    String thumbnailParentUrl = null;
    ImageInfo thumbnail = null;

    public ImageInfo(@Nullable String url) {
        this.url = url;
    }

    public ImageInfo setImageServiceListener(@Nullable ImageServiceListener imageServiceListener) {
        this.imageServiceListener = imageServiceListener;
        return this;
    }

    public ImageInfo setImageLoadListener(FileDownloadListener loadListener) {
        this.loadListener = loadListener;
        return this;
    }

    public ImageInfo setEnterAnimation(@Nullable Animation animation) {
        this.enterAnimation = animation;
        return this;
    }

    public ImageInfo setExitAnimation(@Nullable Animation animation) {
        this.exitAnimation = animation;
        return this;
    }

    public ImageInfo setAnimations(@Nullable Animation enterAnimation, @Nullable Animation exitAnimation) {
        this.enterAnimation = enterAnimation;
        this.exitAnimation = exitAnimation;
        return this;
    }

    public ImageInfo setDefaultResource(int defResource) {
        this.defResource = defResource;
        return this;
    }

    public ImageInfo useDefaultResource(boolean useDefaultResource) {
        this.useDefaultResource = useDefaultResource;
        return this;
    }

    public boolean isUseDefaultResource() {
        return useDefaultResource;
    }

    public ImageInfo setSampleSize(Integer maxSize) {
        this.sampleSize = maxSize;
        return this;
    }

    public ImageInfo setFolder(@Nullable File folder) {
        this.folder = folder;
        return this;
    }

    public ImageInfo setHighQualityScaling(boolean isHightQuality) {
        this.isHighQualityScaling = isHightQuality;
        return this;
    }

    public ImageInfo setCompressFormat(CompressFormat format) {
        this.compressFormat = format;
        return this;
    }

    public ImageInfo setCompression(int compression) {
        this.compression = compression;
        return this;
    }

    public ImageInfo setCompression(CompressFormat format, int compression, boolean isHighQuality) {
        this.compressFormat = format;
        this.compression = compression;
        this.isHighQualityScaling = isHighQuality;
        return this;
    }

    public ImageInfo setBitmapConfig(@NonNull Config bitmapConfig) {
        this.bitmapConfig = bitmapConfig;

        return this;
    }

    public ImageInfo setAssignFailResource(@DrawableRes @Nullable Integer res) {
        this.assignFailResource = res;
        return this;
    }

    public ImageInfo setAssignImmediately(boolean assignImmediately) {
        this.isAssignedImmediately = assignImmediately;
        return this;
    }

    public boolean isAssignedImmediately() {
        return isAssignedImmediately;
    }

    public ImageInfo setImageModifier(@Nullable ImageModifier modifier) {
        this.imageModifier = modifier;
        return this;
    }

    public boolean isBlur() {
        return blur > 0;
    }

    public ImageInfo setBlur(boolean isBlur) {
        this.blur = isBlur ? DEFAULT_BLUR : 0;
        return this;
    }

    public ImageInfo setBlur(@IntRange(from = 0, to = 25) int blur) {
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
        setThumbnailUrl(new ImageInfo(url));
    }

    public ImageInfo setThumbnailUrl(ImageInfo thumbnail) {
        if (!TextUtils.isEmpty(this.thumbnailParentUrl)) {
            // note: thumbnail can not have thumbnail
            throw new IllegalStateException("Can not set thumbnail for a thumbnail!");
        }
        if (thumbnail != null && TextUtils.isEmpty(thumbnail.url)) {
            QLog.d(TAG, "not setting null thumbnail url & data");
            return this;
        }

        this.thumbnail = thumbnail;
        if (this.thumbnail != null) {
            this.thumbnail.thumbnailParentUrl = this.url;
            this.thumbnail.setAssignImmediately(true);
        }

        return this;
    }

    public ImageInfo getThumbnail() {
        return thumbnail;
    }

    public void into(ImageView view) {
        ImageService.getInstance().setImage(this, view);
    }
}