package com.choicely.imageservice;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.TrafficStats;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.media.ExifInterface;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.util.Base64;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;

import com.choicely.imageservice.log.ChoicelyLogService;
import com.choicely.imageservice.utils.ChoicelyStaticUtils;
import com.choicely.imageservice.web.OkFileProgressHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Image manager to handle image loading from server to local disk. Uses url
 * name as local name, to handle caching and conflicts.
 *
 * @author tommy
 */
@SuppressWarnings("unused")
public class ChoicelyImageService extends ChoicelyLogService {

    /**
     * Recommended default compression of 75%.
     */
    public static final int DEFAULT_COMPRESSION = 75;

    @Deprecated
    public static final int DEFAULT_SCALE = 1;

    public static final int DEFAULT_BLUR = 3;

    public static final String EMPTY_IMAGE_TAG = "empty_image_tag";

    private static final int CIS_SOCKET_TAG_ID = 313;

    private int maxTextureSize = -1;

    private final ExecutorService downloadPool;
    private final ExecutorService assignPool;
    private static final String TAG = "CIS";
    private static ChoicelyImageService instance;
    private OkHttpClient web;
    private final Stack<ImageChooser> priorityStack = new Stack<>();
    private final Stack<ImageChooser> taskStack = new Stack<>();
    private final Map<String, List<Pair<ImageChooser, WeakReference<ImageView>>>> mappedViews = Collections
            .synchronizedMap(new HashMap<String, List<Pair<ImageChooser, WeakReference<ImageView>>>>());
    private final Map<String, List<ImageLoadListener>> onDownloadReadyWaitMap = new HashMap<>();

    private final List<String> loading = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private WeakReference<Context> weakContext;
    private File directory;
    private String path;
    private FileSystem fileSystem;

    public static Bitmap blur(Context context, Bitmap bm, int defaultBlur) {
        return ChoicelyImageBlur.blur(context, bm, defaultBlur);
    }

    public enum FileSystem {

        APPLICATION_CACHE,

        EXTERNAL_STORAGE,;

    }

    private ChoicelyImageService(Context context, FileSystem fileSystem, String filePath, int downloadThreads, int assignThreads, OkHttpClient client) {
        super(TAG);
        this.weakContext = new WeakReference<>(context);
        this.fileSystem = fileSystem;
        this.path = filePath;
        downloadPool = Executors.newFixedThreadPool(downloadThreads);
        assignPool = Executors.newFixedThreadPool(assignThreads);
        if (client == null) {
            web = new OkHttpClient.Builder()
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
        } else {
            web = client;
        }

        maxTextureSize = getMaximumTextureSize();

        changeImageDirectory(context, fileSystem, filePath);

    }

    public OkHttpClient getOkWebClient() {
        return web;
    }

    public int getMaximumTextureSize() {
        EGL10 egl = (EGL10) EGLContext.getEGL();

        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        // Initialise
        int[] version = new int[2];
        egl.eglInitialize(display, version);

        // Query total number of configurations
        int[] totalConfigurations = new int[1];
        egl.eglGetConfigs(display, null, 0, totalConfigurations);

        // Query actual list configurations
        EGLConfig[] configurationsList = new EGLConfig[totalConfigurations[0]];
        egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations);

        int[] textureSize = new int[1];
        int maximumTextureSize = 0;

        // Iterate through all the configurations to located the maximum texture size
        for (int i = 0; i < totalConfigurations[0]; i++) {
            // Only need to check for width since opengl textures are always squared
            egl.eglGetConfigAttrib(display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize);

            // Keep track of the maximum texture size
            if (maximumTextureSize < textureSize[0]) {
                maximumTextureSize = textureSize[0];
            }
//            i("GLHelper textureSize[%s]", Integer.toString(textureSize[0]));
        }

        // Release
        egl.eglTerminate(display);
        i("GLHelper Maximum GL texture size: %s", Integer.toString(maximumTextureSize));

        return maximumTextureSize;
    }

    /**
     * Get singleton instance.
     *
     * @return ImageManager instance
     */
    public static ChoicelyImageService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ChoicelyImageService has not been initialized!");
        }
        return instance;
    }

    /**
     * Initialize with image storing directory in Application cache.
     *
     * @param context  Application context to access Cache dir.
     * @param filePath File path inside Cache dir.
     * @param nThreads Number of threads for Downloading and Assigning images. Half
     *                 and half will be used for each.
     */
    public static void init(Context context, String filePath, int nThreads) {
        init(context, FileSystem.APPLICATION_CACHE, filePath, nThreads / 2, nThreads / 2);
    }

    /**
     * Initialize with image storing directory.
     *
     * @param context         Application context to access Cache dir.
     * @param filePath        File path inside Cache dir.
     * @param downloadThreads Number of Threads used to download images.
     * @param assignThreads   Number of Threads used to read images from memory. Downloads
     *                        will be done in separate threads.
     */
    public static void init(Context context, FileSystem fileSystem, String filePath, int downloadThreads, int assignThreads) {
        init(context, fileSystem, filePath, downloadThreads, assignThreads, null);
    }

    /**
     * Initialize with image storing directory.
     *
     * @param context         Application context to access Cache dir.
     * @param filePath        File path inside Cache dir.
     * @param downloadThreads Number of Threads used to download images.
     * @param assignThreads   Number of Threads used to read images from memory. Downloads
     *                        will be done in separate threads.
     * @param client          Custom configured OkHttpClient for ChoicelyImageService. Can
     *                        be null, then default OkHttpClient will be used.
     */
    public static void init(Context context, FileSystem fileSystem, String filePath, int downloadThreads, int assignThreads, @Nullable OkHttpClient client) {
        if (downloadThreads < 1) {
            downloadThreads = 1;
        }
        if (assignThreads < 1) {
            assignThreads = 1;
        }
        if (instance == null) {
            instance = new ChoicelyImageService(context, fileSystem, filePath, downloadThreads, assignThreads, client);
        } else {
            throw new IllegalStateException("ChoicelyImageService is already initialized");
        }
    }

    /**
     * Set different directory for ChoicelyImageService
     *
     * @param context    Application context to access Cache dir.
     * @param fileSystem FileSystem enum to determine is Application cache or external storage used.
     * @param filePath   File path in cache dir.
     */
    public void changeImageDirectory(final Context context, final FileSystem fileSystem, final String filePath) {
        assignPool.execute(new Runnable() {
            @Override
            public void run() {
                changeDirectory(context, fileSystem, filePath);
            }
        });
    }

    private void changeDirectory(Context context, FileSystem fileSystem, String filePath) {
        this.fileSystem = fileSystem;
        this.path = filePath;
        switch (fileSystem) {
            case EXTERNAL_STORAGE:
                if (checkWriteExternalPermission(context)) {
                    directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filePath);
                    break;
                } else {
                    this.fileSystem = FileSystem.APPLICATION_CACHE;
                }
                // we default to application cache if no external storage write permission
            default:
            case APPLICATION_CACHE:
                directory = new File(context.getCacheDir(), filePath);
                break;
        }

        if (directory.mkdirs()) {
            d("[%s]Directory[%s] create", fileSystem, directory.getAbsolutePath());
        }
    }

    private boolean checkWriteExternalPermission(Context context) {
        if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
            return true;
        }
        String permission = "android.permission.WRITE_EXTERNAL_STORAGE";
        int res = context.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Get directory used by ChoicelyImageService
     *
     * @return File
     */
    public File getImageDirectory() {
        return directory;
    }

    /**
     * Get file matching given url.
     *
     * Warning: If you're using application specific file names this wont work.
     *
     * @param url Location of the image file
     * @return File
     */
    @Nullable
    public File getImageFile(String url) {
        return getImageFile(null, url);
    }

    /**
     * Get the File object for the given image.
     *
     * @param url Path to the image location. Can be local url or web url.
     * @return File object.
     */
    @Nullable
    public File getImageFile(File folder, String url) {
        return getImageFile(new ImageChooser(url).setFolder(folder));
    }

    public File getImageFile(ImageChooser info) {
        Context ctx = weakContext.get();
        if (ctx == null || TextUtils.isEmpty(info.url)) {
            return null;
        }
        File folder = info.folder;
        if (folder == null) {
            if (directory == null) {
                changeDirectory(ctx, fileSystem, path);
            }
            folder = directory;
        }
        if (folder.mkdirs()) {
            i("[%s]directory created", folder.getAbsolutePath());
        }
        if (info.url.startsWith("/")) {
            // works for local locations
            return new File(info.url);
        }

        String fileName = "" + info.url.hashCode();
        return new File(folder, fileName);
    }

    /**
     * Load image from web. This method does not assign image to ImageView but
     * returns the resulting Bitmap trough ImageServiceListener
     * imageReady method.
     *
     * NOTE: imageReady method will be called in UI thread.
     *
     * @param url         Location of the image in the Internet or in file system.
     * @param defResource Resource used to set in image view while download is in progress and for storing tag inside ImageView.
     * @param listener    ImageServiceListener to listen for success or failure
     */
    public void loadImage(final String url, final int defResource, final ImageServiceListener listener) {
        loadImage(url, new ImageLoadListener() {

            @Override
            public void onSuccess(File file) {
                final Bitmap bm = getBitmap(file);
                notifyImageReady(listener, url, bm, defResource);
                nextTask();
            }

            @Override
            public void onProgress(float progress) {
            }

            @Override
            public void onFail(int statusCode) {
                notifyImageReady(listener, url, null, defResource);
            }
        });
    }

    /**
     * Load image from network to local disk. If image already exists, this
     * method does nothing.
     *
     * Also does nothing if location is local.
     *
     * @param url          Image location in network
     * @param loadListener ImageLoadListener for listening success or failure of image load
     */
    public void loadImage(@NonNull final String url, @NonNull final ImageLoadListener loadListener) {
        loadImage(new ImageChooser(url), loadListener);
    }

    /**
     * Load image from network to local disk. If image already exists, this
     * method does nothing.
     *
     * NOTE: if image is already being downloaded this method returns null.
     *
     * Also does nothing if location is local.
     *
     * @param info ImageInformation
     */
    public void loadImage(final ImageChooser info) {
        loadImage(info, null);
    }

    public void loadImageInThread(final ImageChooser info) {
        downloadPool.execute(new Runnable() {
            @Override
            public void run() {
                loadImage(info, null);
            }
        });

    }

    /**
     * Load image from network to local disk. If image already exists, this
     * method does nothing.
     *
     * NOTE: if image is already being downloaded this method returns null.
     *
     * Also does nothing if location is local.
     *
     * @param info ImageInformation
     */
    public void loadImage(final ImageChooser info, final ImageLoadListener mainLoadListener) {
        File resultFile = null;
        if (TextUtils.isEmpty(info.url)) {
            e("loadImage: Image url or file name empty");
            if (mainLoadListener != null) {
                mainLoadListener.onFail(400);
            }
            if (info.loadListener != null) {
                info.loadListener.onFail(400);
            }
            return;
        }

        if (hasImage(info)) {
            d("[%s]image already loaded", info.url);
            resultFile = getImageFile(info);

            if (mainLoadListener != null) {
                mainLoadListener.onSuccess(resultFile);
            }
            if (info.loadListener != null) {
                info.loadListener.onSuccess(resultFile);
            }
            return;
        }

        synchronized (loading) {
            if (loading.contains(info.url)) {
                w("Already loading url[%s]", info.url);
                addDownloadCompleteListener(info.url, info.loadListener);
                addDownloadCompleteListener(info.url, mainLoadListener);
                return;
            }
            loading.add(info.url);
        }
        d("Load: %s", info.url);
        final File file = getImageFile(info);
        Request.Builder requestBuilder = new Request.Builder();
        Request r = requestBuilder.url(info.url).get().build();
        Response response;
        try {
            TrafficStats.setThreadStatsTag(CIS_SOCKET_TAG_ID);
            response = web.newCall(r).execute();
        } catch (Exception e) {
            w(e, "Problem performing request[%s]", info.url);
            response = null;
        }
        int statusCode;
        boolean success;
        if (response != null && response.isSuccessful()) {
            // Handle success
            success = true;
            statusCode = response.code();
            resultFile = new OkFileProgressHandler(file) {

                @Override
                public void onProgress(float progress) {
//                    super.onProgress(progress);
                    instance.d("[%s]LoadingProgress[%s]", info.url, Float.toString(progress));

                    if (mainLoadListener != null) {
                        mainLoadListener.onProgress(progress);
                    }
                    if (info.loadListener != null) {
                        info.loadListener.onProgress(progress);
                    }
                }

            }.handleResponse(response);

            if (mainLoadListener != null) {
                mainLoadListener.onSuccess(resultFile);
            }
            if (info.loadListener != null) {
                info.loadListener.onSuccess(resultFile);
            }
        } else {
            // Handle error
            success = false;
            statusCode = -1;
            if (response != null) {
                statusCode = response.code();
                ChoicelyStaticUtils.close(response.body());
            }
            if (mainLoadListener != null) {
                mainLoadListener.onFail(statusCode);
            }
            if (info.loadListener != null) {
                info.loadListener.onFail(statusCode);
            }

        }

        notifyDownloadFinished(info.url, success, statusCode, resultFile);
        loadingReady(info.url);
    }

    private void addDownloadCompleteListener(String url, ImageLoadListener loadListener) {
        if (TextUtils.isEmpty(url) || loadListener == null) {
            return;
        }
        synchronized (onDownloadReadyWaitMap) {
            List<ImageLoadListener> list = onDownloadReadyWaitMap.get(url);
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(loadListener);
            onDownloadReadyWaitMap.put(url, list);
        }
    }

    private void notifyDownloadFinished(String url, boolean success, int statusCode, File file) {
        synchronized (onDownloadReadyWaitMap) {
            List<ImageLoadListener> list = onDownloadReadyWaitMap.get(url);
            if (list != null) {
                for (ImageLoadListener listener : list) {
                    if (listener != null) {
                        if (success) {
                            listener.onSuccess(file);
                        } else {
                            listener.onFail(statusCode);
                        }
                    }
                }
            }
            onDownloadReadyWaitMap.put(url, null);
        }
    }

    /**
     * Scale image down in given format and quality so that the longer side will
     * be given max dimension.
     *
     * @param originalFile File that will be scaled down
     * @param targetFile   Location of the image in the Internet or in file system.
     * @param maxDimension Maximum size for height or width.
     * @param highQuality  Is image scaled in high quality or not
     * @param format       CompressFormat either PNG or JPG recommended
     * @param quality      Compression quality
     * @return File
     */
    public File scaleImageDown(File originalFile, File targetFile, int maxDimension, boolean highQuality, CompressFormat format, int quality) {
        return scaleImageDown(originalFile, targetFile, maxDimension, highQuality, format, quality, Config.ARGB_8888);
    }

    /**
     * Scale image down in given format and quality so that the longer side will
     * be given max dimension.
     *
     * @param originalFile File that will be scaled down
     * @param targetFile   Location of the image in the Internet or in file system.
     * @param maxDimension Maximum size for height or width.
     * @param highQuality  Is image scaled in high quality or not
     * @param format       CompressFormat either PNG or JPG recommended
     * @param quality      Compression quality
     * @return File
     */
    public File scaleImageDown(File originalFile, File targetFile, int maxDimension, boolean highQuality, CompressFormat format, int quality, Config bitmapConfig) {
        if (targetFile == null) {
            w("Could not scale image down, target File is null");
            return null;
        }
        if (bitmapConfig == null) {
            bitmapConfig = Config.ARGB_8888;
        }

        Bitmap bm;
        if (highQuality) {
            bm = createHighQualityScaledBitmapFromOriginal(originalFile, maxDimension, bitmapConfig);
        } else {
            bm = createScaledBitmapFromOriginal(originalFile, maxDimension, bitmapConfig);
        }
        if (bm != null) {
            saveImageToFile(bm, targetFile, format, quality);
        } else {
            w("Could not scale image[%s] down as bitmap could not be read!", originalFile.getAbsoluteFile());
        }

        d("[%s] scaled", targetFile.getAbsolutePath());
        return targetFile;
    }

    public void saveImageToFile(Bitmap bm, File targetFile) {
        saveImageToFile(bm, targetFile, CompressFormat.JPEG, DEFAULT_COMPRESSION);
    }

    public void saveImageToFile(Bitmap bm, File targetFile, CompressFormat format, int quality) {
        if (bm == null) {
            return;
        }
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream(targetFile);

            bm.compress(format, quality, fo);
        } catch (FileNotFoundException e) {
            w(e, "Could not find file to scale");
        } finally {
            try {
                if (fo != null) {
                    fo.close();
                }
            } catch (IOException e) {
                w(e, "Could not close FileOutputStream");
            }
        }
        d("SavedImage: %s", targetFile.getAbsolutePath());
    }

    public Bitmap createHighQualityScaledBitmapFromOriginal(File file, int maxDimension, Config bitmapConfig) {
        if (file == null) {
            return null;
        }
        String originPath = file.getAbsolutePath();
        d("HQ Scaling down: %s", originPath);
        // We decode the image size first so we can scale it correctly
        // Without this we run out of memory fast
        Options opt = new Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(originPath, opt);
        double scale = 1;
        if (opt.outWidth > opt.outHeight && opt.outWidth > maxDimension) {
            scale = (double) maxDimension / (double) opt.outWidth;
        } else if (opt.outHeight > maxDimension) {
            scale = (double) maxDimension / (double) opt.outHeight;
        }
        int dstWidth = (int) (opt.outWidth * scale);
        int dstHeight = (int) (opt.outHeight * scale);
        int sampleSize = 1;
        if (opt.outHeight > 2 * maxDimension || opt.outWidth > 2 * maxDimension) {
            sampleSize = (int) Math.pow(
                    2,
                    (int) Math.round(Math.log((2 * maxDimension) / (double) Math.max(opt.outHeight, opt.outWidth))
                            / Math.log(0.5)));
        }

        Options opt2 = new Options();
        opt2.inSampleSize = sampleSize;
        opt2.inPreferredConfig = bitmapConfig;

        Bitmap bm = null;
        try {
            bm = Bitmap.createScaledBitmap(BitmapFactory.decodeFile(originPath, opt2), dstWidth, dstHeight, true);
        } catch (OutOfMemoryError e) {
            callGC();
            w("Memory running low.");
        }

        int rotation = getExifRotation(new File(originPath));
        if (bm != null && rotation != 0) {
            d("image[%s] rotating[%s]", originPath, rotation);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(rotation);
            bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
        }

        return bm;
    }

    /**
     * Scales locally stored image to match the given maxDimension.
     *
     * @param file         Image file
     * @param maxDimension Maximum size for height or width.
     * @return Scaled Bitmap or null if bitmap could not be decoded from file.
     */
    public Bitmap createScaledBitmapFromOriginal(File file, int maxDimension, Config bitmapConfig) {
        if (file == null) {
            return null;
        }
        String originPath = file.getAbsolutePath();
        // We decode the image size first so we can scale it correctly
        // Without this we run out of memory fast
        Options opt = getBitmapOptions(file);
        int scale = 1;
        if (opt.outHeight > maxDimension || opt.outWidth > maxDimension) {
            scale = (int) Math.pow(
                    2,
                    (int) Math.round(Math.log(maxDimension / (double) Math.max(opt.outHeight, opt.outWidth))
                            / Math.log(0.5)));

            // If target size is smaller than origin then scale should be at least two
            scale = Math.max(scale, 2);
        }

        Options opt2 = new Options();
        opt2.inSampleSize = scale;
        opt2.inPreferredConfig = bitmapConfig;

        d("max[%d]sample[%d]origin[%d, %d]image[%s]path[%s]", maxDimension, scale, opt.outWidth, opt.outHeight, originPath, originPath);
        Bitmap bm = null;
        try {
            bm = BitmapFactory.decodeFile(originPath, opt2);
        } catch (OutOfMemoryError e) {
            callGC();
            e("Memory running low.");
        }
        int rotation = getExifRotation(new File(originPath));
        if (bm != null && rotation != 0) {
            d("image[%s] rotating[%s]", originPath, rotation);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(rotation);
            bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
        }

        return bm;
    }

    public static Bitmap scaleBitmapDown(Bitmap realImage, float maxImageSize) {
        float ratio = Math.min(
                maxImageSize / (float) realImage.getWidth(),
                maxImageSize / (float) realImage.getHeight());
        int width = Math.round(ratio * realImage.getWidth());
        int height = Math.round(ratio * realImage.getHeight());

        return Bitmap.createScaledBitmap(realImage, width, height, true);
    }

    private Options getBitmapOptions(File file) {
        Options opt = new Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opt);

        return opt;
    }

    /**
     * Get Bitmap instance of image. If image is not cached to local
     * disk, it is loaded first. Loading from disk or network is done in calling
     * thread! This should be called outside UI Thread.
     *
     * This method does not use Cache.
     *
     * @param file File of the image file.
     * @return Bitmap of the image or null if the cover path is invalid
     */
    public Bitmap getBitmap(File file) {
        return getBitmap(file, null);
    }

    public Bitmap getBitmap(ImageChooser info) {
        return getBitmap(getImageFile(info), info);
    }

    /**
     * Get Bitmap instance of image. If image is not cached to local
     * disk, it is loaded first. Loading from disk or network is done in calling
     * thread! This should be called outside UI Thread.
     *
     * @param file File of the image file.
     * @param info Image information to get the image from Cache. Can be null;
     * @return Bitmap of the image or null if the cover path is invalid
     */
    public Bitmap getBitmap(File file, @Nullable ImageChooser info) {
        if (file == null || !file.canRead()) {
            if (file != null) {
                w("Can't read location[%s]", file.getAbsolutePath());
            } else {
                w("Can't read location[null]");
            }
            return null;
        }

        Bitmap bm = null;
        try {
            Options opt = new Options();
            if (info != null) {
                opt.inPreferredConfig = info.bitmapConfig;
            } else {
                opt.inPreferredConfig = Config.ARGB_8888;
            }

            bm = BitmapFactory.decodeFile(file.getAbsolutePath(), opt);
            int rotation = getExifRotation(file);
            if (rotation != 0) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(rotation);
                bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
            }
        } catch (OutOfMemoryError e) {
            w("Running out of memory");
            callGC();
        }
        if (bm == null) {
            w("Bitmap [%s] could not be made", file.getAbsolutePath());
        }

        return bm;
    }

    public void setImage(final ImageChooser info, final ImageView imageView) {
        if (imageView == null || info == null) {
            d("ImageView or info empty");
            return;
        }
        String tag = (String) imageView.getTag(R.id.cis_tag_id);
        if (tag != null && tag.equals(info.url)) {
            d("Same image already queued: %s", tag);
            notifyImageReady(info, null);
            return;
        }
        imageView.clearAnimation();
        if (info.isUseDefaultResource()) {
            imageView.setImageResource(info.defResource);
        }
        imageView.setTag(R.id.cis_tag_id, TextUtils.isEmpty(info.url) ? EMPTY_IMAGE_TAG : info.url);

        if (TextUtils.isEmpty(info.url)) {
            d("url empty");
            if (!info.isUseDefaultResource() && info.assignFailResource != null) {
                imageView.setImageResource(info.assignFailResource);
            }
            notifyImageReady(info, null);
            return;
        }

        assignPool.execute(new Runnable() {

            @Override
            public void run() {
                final WeakReference<ImageView> weakImage = new WeakReference<>(imageView);
                if (info.isAssignedImmediately && hasImage(info)) {
                    d("AssignImmediately[%s]", info.url);
                    assignImage(imageView, info);
                    return;
                }
                // note: thumbnail can not have thumbnail
                if (!hasImage(info) && info.hasThumbnail()) {
                    final ImageChooser thumbnail = info.getThumbnail();
                    thumbnail.setImageServiceListener(new ImageServiceListener() {
                        @Override
                        public void imageReady(String imageUrl, Bitmap image, int defaultResId) {
                            d("Thumbnail[%s] ready", imageUrl);
                            info.setEnterAnimation(null); // clear enter animation if thumbnail assign was success
                            setImage(info, weakImage);
                        }

                        @Override
                        public void imageError(String imageUrl, int resultCode) {
                            w("Thumbnail[%s] error", imageUrl);
                            setImage(info, weakImage);
                        }
                    });

                    if (!TextUtils.isEmpty(thumbnail.url)) {
                        d("ImageThumbnail[%s]", thumbnail.url);
                        setImage(thumbnail, weakImage);
                    }
                } else {
                    setImage(info, weakImage);
                }
            }
        });
    }

    public void clearTag(ImageView imageView) {
        imageView.setTag(R.id.cis_tag_id, null);
    }

    private void setImage(final ImageChooser info, final WeakReference<ImageView> weakImage) {
        synchronized (mappedViews) {
            addViewToMap(info, weakImage);
        }
        boolean isLoading;
        synchronized (loading) {
            isLoading = loading.contains(info.url);
        }

        if (isLoading || hasImage(info)) {
            synchronized (taskStack) {
                d("pushing task[%s]", info.url);
                taskStack.push(info);
            }
        } else {
            synchronized (priorityStack) {
                d("pushing priority task[%s]", info.url);
                priorityStack.push(info);
            }
        }
        nextTask();
    }

    private void addViewToMap(ImageChooser info, WeakReference<ImageView> weakImage) {
        List<Pair<ImageChooser, WeakReference<ImageView>>> list = mappedViews.get(info.url);
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(Pair.create(info, weakImage));
        mappedViews.put(info.url, list);
    }

    private void loadingReady(String url) {
        synchronized (loading) {
            loading.remove(url);
        }
        i("Image [%s] download complete", url);
    }

    private void nextTask() {
        assignPool.execute(new Runnable() {

            @Override
            public void run() {
                ImageChooser info = null;
                synchronized (priorityStack) {
                    if (!priorityStack.isEmpty()) {
                        info = priorityStack.pop();
                        d("poping priority[%s]", info.url);
                    }
                }
                if (info != null) {
                    synchronized (loading) {
                        if (loading.contains(info.url)) {
                            synchronized (taskStack) {
                                taskStack.push(info);
                            }
                            return;
                        }
                    }
                    downloadPool.execute(new DownloadRunnable(info));
                    nextTask();
                    return;
                }

                synchronized (taskStack) {
                    if (!taskStack.isEmpty()) {
                        info = taskStack.pop();
                        d("poping task[%s]", info.url);
                    } else {
                        d("TaskStack empty");
                        return;
                    }
                }
                synchronized (loading) {
                    if (loading.contains(info.url)) {
                        synchronized (taskStack) {
                            taskStack.add(info);
                        }
                        d("loading not finished");
                        return;
                    }
                }

                List<Pair<ImageChooser, WeakReference<ImageView>>> viewList = Collections.emptyList();
                synchronized (mappedViews) {
                    if (mappedViews.containsKey(info.url)) {
                        viewList = mappedViews.remove(info.url);
                    }
                }
                for (Pair<ImageChooser, WeakReference<ImageView>> pair : viewList) {
                    WeakReference<ImageView> weakImage = pair.second;
                    final ImageView imageView;
                    if (weakImage != null) {
                        imageView = weakImage.get();
                    } else {
                        imageView = null;
                        d("ImageView reference dropped");
                    }

                    if (imageView == null) {
                        d("ImageView reference expired");
                    } else {
                        assignPool.execute(new AssignRunnable(pair.first, imageView));
                    }
                }

                nextTask();
            }
        });
    }

    private class DownloadRunnable implements Runnable {

        private final ImageChooser info;

        public DownloadRunnable(ImageChooser info) {
            this.info = info;
        }

        @Override
        public void run() {
            imageLoadTask(info);
        }

    }

    private class AssignRunnable implements Runnable {

        private final ImageChooser info;
        private final ImageView iv;

        public AssignRunnable(ImageChooser info, ImageView iv) {
            this.info = info;
            this.iv = iv;
        }

        @Override
        public void run() {
            assignImage(iv, info);
            nextTask();
        }

    }

    private void imageLoadTask(final ImageChooser info) {
        loadImage(info, new ImageLoadListener() {

            @Override
            public void onSuccess(File file) {
                List<Pair<ImageChooser, WeakReference<ImageView>>> viewList = Collections.emptyList();
                synchronized (mappedViews) {
                    if (mappedViews.containsKey(info.url)) {
                        viewList = mappedViews.remove(info.url);
                    }
                }

                if (info.loadListener != null) {
                    info.loadListener.onSuccess(file);
                }

                for (Pair<ImageChooser, WeakReference<ImageView>> pair : viewList) {
                    WeakReference<ImageView> weakImage = pair.second;
                    if (weakImage != null) {
                        assignImage(weakImage.get(), pair.first);
                    } else {
                        d("[%s] no weak image", info.url);
                    }
                }

                nextTask();
            }

            @Override
            public void onProgress(float progress) {
            }

            @Override
            public void onFail(int statusCode) {
                w("[%s]Download failed: %s", statusCode, info.url);
                notifyImageReady(info, null);

                List<Pair<ImageChooser, WeakReference<ImageView>>> viewList = Collections.emptyList();
                synchronized (mappedViews) {
                    if (mappedViews.containsKey(info.url)) {
                        viewList = mappedViews.remove(info.url);
                    }
                }
                for (Pair<ImageChooser, WeakReference<ImageView>> pair : viewList) {
                    WeakReference<ImageView> weakImage = pair.second;
                    if (weakImage != null) {
                        assignImage(weakImage.get(), info);
                    } else {
                        d("[%s] no weak image", info.url);
                    }
                }

                nextTask();
            }
        });
    }

    private void assignImage(final ImageView view, final ImageChooser info) {
        File file = getImageFile(info);
        Bitmap image;
        if (view == null) {
            notifyImageReady(info, null);
            return;
        } else if (file == null || !file.exists() || !file.canRead()) {
            d("Image File [%s] null", info.url);
            setAssignFailResource(view, info);
            return;
        } else {
            Options opt = getBitmapOptions(file);
            if (info.sampleSize != null && info.sampleSize > 0) {
                d("[%s]ScalingTo[%d] origin[%d, %d]", info.url, info.sampleSize, opt.outWidth, opt.outHeight);
                image = createScaledBitmapFromOriginal(file, info.sampleSize, info.bitmapConfig);
            } else if (maxTextureSize > 0 && Math.max(opt.outWidth, opt.outHeight) >= maxTextureSize) {
                w("Maximum texture size[%d] exceeded by image[%d, %d]", maxTextureSize, opt.outWidth, opt.outHeight);
                image = createScaledBitmapFromOriginal(file, maxTextureSize, info.bitmapConfig);
            } else {
                image = getBitmap(file, info);
            }
        }
        if (info.imageModifier != null && image != null) {
            image = info.imageModifier.modify(image);
        }

        if (image == null) {
            d("Image [%s] was null [%s]", info.url, file.getAbsolutePath());
            setAssignFailResource(view, info);
            notifyImageReady(info, null);
            return;
        }
        if (info.blur > 0) {
            image = ChoicelyImageBlur.blur(view.getContext(), image, info.blur);
        }

        d("bytes[%s]size[%s,%s]density[%s]image[%s]", (image.getRowBytes() * image.getHeight()), image.getWidth(), image.getHeight(), image.getDensity(), info.url);

        final Bitmap assignedImage = image;
        uiHandler.post(new Runnable() {

            @Override
            public void run() {
                String tag = (String) view.getTag(R.id.cis_tag_id);
                if (tag != null &&
                        ((tag.equals(info.url) || tag.equals(info.thumbnailParentUrl)) // if correct image expected
                                || (info.hasThumbnail() && tag.equals(info.thumbnail.url)))) { // or if thumbnail already set
                    view.clearAnimation();

                    if (info.isCrossFade) {
                        Drawable original = view.getDrawable();
                        if (original != null && assignedImage != null) {
                            Drawable backgrounds[] = new Drawable[2];
                            backgrounds[0] = view.getDrawable();
                            backgrounds[1] = new BitmapDrawable(view.getResources(), assignedImage);

                            TransitionDrawable crossFade = new TransitionDrawable(backgrounds);
                            crossFade.setCrossFadeEnabled(true);
                            view.setImageDrawable(crossFade);
                            crossFade.startTransition(info.crossFadeDuration);
                        } else {
                            view.setImageBitmap(assignedImage);
                        }
                    } else if (info.exitAnimation != null) {
                        info.exitAnimation.setAnimationListener(new OnAnimationEnd(assignedImage, view, info));
                        d("[%s]Exit animation started", info.url);
                        view.startAnimation(info.exitAnimation);
                    } else {
                        view.setImageBitmap(assignedImage);
                        if (info.enterAnimation != null) {
                            d("[%s]Enter animation started", info.url);
                            view.startAnimation(info.enterAnimation);
                        }
                    }
                    notifyImageReady(info, assignedImage);
                    i("Image[%s] set successfully", info.url);
                } else {
                    d("Different image queued [%s / %s] ", info.url, tag);
                }

            }
        });
    }

    private void setAssignFailResource(final ImageView view, final ImageChooser info) {
        if (info.assignFailResource == null) {
            return;
        }
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                view.setImageResource(info.assignFailResource);
            }
        });
    }

    private void notifyImageReady(ImageChooser info, Bitmap image) {
        notifyImageReady(info.imageServiceListener, info.url, image, info.defResource);
    }

    private void notifyImageReady(final ImageServiceListener listener, final String url, final Bitmap image, final int defResource) {
        if (listener == null) {
            return;
        }
        d("NotifyImageReady[%s]", url);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.imageReady(url, image, defResource);
            }
        });
    }

    private class OnAnimationEnd implements AnimationListener {

        private ImageView view;
        private Bitmap image;
        private ImageChooser info;

        private OnAnimationEnd(Bitmap image, ImageView view, ImageChooser info) {
            this.image = image;
            this.view = view;
            this.info = info;
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

    /**
     * Checks does the local file of the image.
     *
     * NOTE: this only checks from ChoicelyImageService default directory.
     *
     * @param url Image file name.
     * @return true if local file exists and false is no file exist.
     */
    public boolean hasImage(String url) {
        File f = getImageFile(url);
        return f != null && f.exists();
    }

    /**
     * Checks does the local file of the image.
     *
     * @param info Image information.
     * @return true if local file exists and false is no file exist.
     */
    public boolean hasImage(ImageChooser info) {
        File f = getImageFile(info);
        return f != null && f.exists();
    }

    /**
     * Delete all images downloaded with ChoicelyImageService.
     */
    public void deleteFiles() {
        downloadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (directory == null) {
                    return;
                }
                File[] files = directory.listFiles();
                if (files == null) {
                    return;
                }
                for (File child : files) {
                    deleteRecursive(child, null);
                }
            }
        });
    }

    /**
     * Delete images that are older than the given System time.
     *
     * @param timeModified Time in milliseconds, any file older than this will be deleted
     */
    public void deleteFilesOlderThan(final long timeModified) {
        downloadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (directory == null || !directory.canRead() || !directory.isDirectory()) {
                    return;
                }
                File[] files = directory.listFiles();
                if (files == null) {
                    return;
                }
                try {
                    for (File child : files) {
                        deleteRecursive(child, timeModified);
                    }
                } catch (Exception e) {
                    w(e, "Problem deleting children");
                }
            }
        });
    }

    private void deleteRecursive(File fileOrDirectory, Long lastModified) {
        if (fileOrDirectory == null) {
            return;
        }
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child, lastModified);
                }
            }
        }
        if (lastModified == null || fileOrDirectory.lastModified() < lastModified) {
            if (fileOrDirectory.delete()) {
                d("Image[%s] Delete success", fileOrDirectory.getAbsolutePath());
            } else {
                w("Image[%s] delete failed", fileOrDirectory.getAbsolutePath());
            }
        }
    }

    /**
     * Set correct rotation to ImageView based on given files
     * ExifInterface data.
     *
     * NOTE: This method only works after API 11.
     *
     * @param iv   ImageView that the rotation will be applied to
     * @param file Image file the exif data will be loaded from
     */
    @TargetApi(VERSION_CODES.HONEYCOMB)
    public void checkExifRotation(ImageView iv, File file) {
        if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
            int rotation = getExifRotation(file);
            iv.setRotation(rotation);
        }
    }

    /**
     * Get image rotation information from ExifInterface data from given
     * File.
     *
     * @param file Image file the exif data will be loaded from
     * @return Image rotation
     */
    public int getExifRotation(File file) {
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(file.getAbsolutePath());
        } catch (IOException e) {
            d(e, "Unable to get exif data");
        }

        int exifRotation = ExifInterface.ORIENTATION_NORMAL;
        if (exif != null) {
            exifRotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        }

        int rotation;
        switch (exifRotation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotation = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotation = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotation = 270;
                break;
            default:
                rotation = 0;
                break;
        }

        return rotation;
    }

    private void callGC() {
        d("calling GC");
        System.gc();
    }

}