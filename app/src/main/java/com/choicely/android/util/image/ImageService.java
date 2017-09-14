package com.choicely.android.util.image;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.media.ExifInterface;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.ImageView;

import com.choicely.android.util.image.log.LogService;
import com.choicely.android.util.image.util.FileUtils;
import com.choicely.android.util.image.util.ImageUtils;
import com.choicely.android.util.image.web.FileDownloadListener;
import com.choicely.android.util.image.web.FileDownloader;
import com.choicely.android.util.image.web.OkImageFileDownloader;

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

/**
 * Image manager to handle image loading from server to local disk. Uses url
 * name as local name, to handle caching and conflicts.
 *
 * @author tommy
 */
@SuppressWarnings("unused")
public class ImageService extends LogService {

    /**
     * Recommended default compression of 75%.
     */
    public static final int DEFAULT_COMPRESSION = 75;

    public static final String EMPTY_IMAGE_TAG = "empty_image_tag";

    private int maxTextureSize = -1;

    private final ExecutorService downloadPool;
    private final ExecutorService assignPool;
    private static final String TAG = "ImageService";
    private static ImageService instance;
    private FileDownloader downloader;
    private final Stack<ImageInfo> priorityStack = new Stack<>();
    private final Stack<ImageInfo> taskStack = new Stack<>();
    private final Map<String, List<Pair<ImageInfo, WeakReference<ImageView>>>> mappedViews = Collections
            .synchronizedMap(new HashMap<String, List<Pair<ImageInfo, WeakReference<ImageView>>>>());
    private final Map<String, List<FileDownloadListener>> onDownloadReadyWaitMap = new HashMap<>();

    private final List<String> loading = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Context ctx;
    private File directory;
    private String path;
    private FileSystem fileSystem;

    public enum FileSystem {

        APPLICATION_CACHE,

        EXTERNAL_STORAGE,;

    }

    private ImageService(Context context, FileSystem fileSystem, String filePath, int downloadThreads, int assignThreads, boolean debug, FileDownloader downloader) {
        super(TAG);
        setDebug(debug);
        this.ctx = context;
        this.fileSystem = fileSystem;
        this.path = filePath;
        downloadPool = Executors.newFixedThreadPool(downloadThreads);
        assignPool = Executors.newFixedThreadPool(assignThreads);
        if (downloader == null) {
            this.downloader = new OkImageFileDownloader(debug);
        } else {
            this.downloader = downloader;
        }

        maxTextureSize = ImageUtils.getMaximumTextureSize();

        changeImageDirectory(fileSystem, filePath);

    }

    /**
     * Get singleton instance.
     *
     * @return ImageManager instance
     */
    public static ImageService getInstance() {
        if (instance == null) {
            throw new IllegalStateException(TAG + " has not been initialized!");
        }
        return instance;
    }

    public void setDownloader(FileDownloader downloader) {
        this.downloader = downloader;
    }

    public static void init(Context context) {
        init(context, FileSystem.APPLICATION_CACHE, "images/", 5, 5);
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
        init(context, fileSystem, filePath, downloadThreads, assignThreads, false, null);
    }

    /**
     * Initialize with image storing directory.
     *
     * @param context         Application context to access Cache dir.
     * @param filePath        File path inside Cache dir.
     * @param downloadThreads Number of Threads used to download images.
     * @param assignThreads   Number of Threads used to read images from memory. Downloads
     *                        will be done in separate threads.
     * @param downloader      Custom FileDownloader
     */
    public static void init(Context context, FileSystem fileSystem, String filePath, int downloadThreads, int assignThreads, boolean debug, @Nullable FileDownloader downloader) {
        if (downloadThreads < 1) {
            downloadThreads = 1;
        }
        if (assignThreads < 1) {
            assignThreads = 1;
        }
        if (instance == null) {
            instance = new ImageService(context, fileSystem, filePath, downloadThreads, assignThreads, debug, downloader);
        } else {
            throw new IllegalStateException(TAG + " is already initialized");
        }
    }

    /**
     * Set different directory for {@link ImageService}
     *
     * @param fileSystem FileSystem enum to determine is Application cache or external storage used.
     * @param filePath   File path in cache dir.
     */
    public void changeImageDirectory(final FileSystem fileSystem, final String filePath) {
        assignPool.execute(new Runnable() {
            @Override
            public void run() {
                changeDirectory(fileSystem, filePath);
            }
        });
    }

    private void changeDirectory(FileSystem fileSystem, String filePath) {
        this.fileSystem = fileSystem;
        this.path = filePath;
        switch (fileSystem) {
            case EXTERNAL_STORAGE:
                if (checkWriteExternalPermission(ctx)) {
                    directory = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filePath);
                    break;
                } else {
                    e("Warning: no write permission to external storage");
                    this.fileSystem = FileSystem.APPLICATION_CACHE;
                }
                // we default to application cache if no external storage write permission
            default:
            case APPLICATION_CACHE:
                directory = new File(ctx.getCacheDir(), filePath);
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
     * Get directory used by {@link ImageService}
     *
     * @return {@link File}
     */
    public File getImageDirectory() {
        return directory;
    }

    public File getImageFile(ImageInfo info) {
        if (TextUtils.isEmpty(info.url)) {
            return null;
        }
        File folder = info.folder;
        if (folder == null) {
            if (directory == null) {
                changeDirectory(fileSystem, path);
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
     * Load image from network to local disk. If image already exists, this
     * method does nothing.
     * <p/>
     * NOTE: if image is already being downloaded this method returns null.
     * <p/>
     * Also does nothing if location is local.
     *
     * @param info ImageInformation
     */
    public void loadImage(final ImageInfo info) {
        internalLoadImage(info, info.loadListener);
    }

    public void loadImageInThread(final ImageInfo info) {
        downloadPool.execute(new Runnable() {
            @Override
            public void run() {
                internalLoadImage(info, info.loadListener);
            }
        });

    }

    /**
     * Load image from network to local disk. If image already exists, this
     * method does nothing.
     * <p/>
     * NOTE: if image is already being downloaded this method returns null.
     * <p/>
     * Also does nothing if location is local.
     *
     * @param info ImageInformation
     */
    private void internalLoadImage(final ImageInfo info, final FileDownloadListener listener) {
        File resultFile;
        if (TextUtils.isEmpty(info.url)) {
            e("loadImage: Image url or file name empty");
            if (listener != null) {
                listener.onError(-1);
            }
            return;
        }

        if (hasImage(info)) {
            d("[%s]image already loaded", info.url);
            resultFile = getImageFile(info);

            if (listener != null) {
                // TODO: Should we have status code 200 here?
                // TODO: Should we check has the file updated?
                listener.onSuccess(1, resultFile);
            }
            return;
        }

        synchronized (loading) {
            if (loading.contains(info.url)) {
                w("Already loading url[%s]", info.url);
                addDownloadCompleteListener(info.url, info.loadListener);
                addDownloadCompleteListener(info.url, listener);
                return;
            }
            loading.add(info.url);
        }
        d("Load: %s", info.url);
        final File targetFile = getImageFile(info);

        downloader.downloadFile(info.url, targetFile, new FileDownloadListener() {
            @Override
            public void onSuccess(int statusCode, File file) {
                if (listener != null) {
                    listener.onSuccess(statusCode, file);
                }
                notifyDownloadFinished(info.url, true, statusCode, file);
                loadingReady(info.url);
            }

            @Override
            public void onError(int statusCode) {
                if (listener != null) {
                    listener.onError(statusCode);
                }
                notifyDownloadFinished(info.url, false, statusCode, null);
                loadingReady(info.url);
            }

            @Override
            public void onProgress(float progress) {
                if (listener != null) {
                    listener.onProgress(progress);
                }
            }
        });
    }

    private void addDownloadCompleteListener(String url, FileDownloadListener loadListener) {
        if (TextUtils.isEmpty(url) || loadListener == null) {
            return;
        }
        synchronized (onDownloadReadyWaitMap) {
            List<FileDownloadListener> list = onDownloadReadyWaitMap.get(url);
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(loadListener);
            onDownloadReadyWaitMap.put(url, list);
        }
    }

    private void notifyDownloadFinished(String url, boolean success, int statusCode, File file) {
        synchronized (onDownloadReadyWaitMap) {
            List<FileDownloadListener> list = onDownloadReadyWaitMap.get(url);
            if (list != null) {
                for (FileDownloadListener listener : list) {
                    if (listener != null) {
                        if (success) {
                            listener.onSuccess(statusCode, file);
                        } else {
                            listener.onError(statusCode);
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

        FileOutputStream fo = null;
        try {
            Bitmap bm;
            if (highQuality) {
                bm = createHighQualityScaledBitmapFromOriginal(originalFile, maxDimension, bitmapConfig);
            } else {
                bm = createScaledBitmapFromOriginal(originalFile, maxDimension, bitmapConfig);
            }
            if (bm != null) {
                fo = new FileOutputStream(targetFile);

                bm.compress(format, quality, fo);
            } else {
                w("Could not scale image[%s] down as bitmap could not be read!", originalFile.getAbsoluteFile());
            }
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

        d("[%s] scaled", targetFile.getAbsolutePath());
        return targetFile;
    }

    private Bitmap createHighQualityScaledBitmapFromOriginal(File file, int maxDimension, Config bitmapConfig) {
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
            System.gc();
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
    private Bitmap createScaledBitmapFromOriginal(File file, int maxDimension, Config bitmapConfig) {
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
            System.gc();
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
                (float) maxImageSize / realImage.getWidth(),
                (float) maxImageSize / realImage.getHeight());
        int width = Math.round((float) ratio * realImage.getWidth());
        int height = Math.round((float) ratio * realImage.getHeight());

        return Bitmap.createScaledBitmap(realImage, width, height, true);
    }

    private Options getBitmapOptions(File file) {
        Options opt = new Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opt);

        return opt;
    }

    /**
     * Get {@link Bitmap} instance of image. If image is not cached to local
     * disk, it is loaded first. Loading from disk or network is done in calling
     * thread! This should be called outside UI Thread.
     * <p/>
     * This method does not use Cache.
     *
     * @param file File of the image file.
     * @return {@link Bitmap} of the image or null if the cover path is invalid
     */
    public Bitmap getBitmap(File file) {
        return getBitmap(file, null);
    }

    public Bitmap getBitmap(ImageInfo info) {
        return getBitmap(getImageFile(info), info);
    }

    /**
     * Get {@link Bitmap} instance of image. If image is not cached to local
     * disk, it is loaded first. Loading from disk or network is done in calling
     * thread! This should be called outside UI <color name="transparent">#0000</color>Thread.
     *
     * @param file File of the image file.
     * @param info Image information to get the image from Cache. Can be null;
     * @return {@link Bitmap} of the image or null if the cover path is invalid
     */
    public Bitmap getBitmap(File file, @Nullable ImageInfo info) {
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
            System.gc();
        }
        if (bm == null) {
            w("Bitmap [%s] could not be made", file.getAbsolutePath());
        }

        return bm;
    }

    public void setImage(final ImageInfo info, final ImageView imageView) {
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
                    final ImageInfo thumbnail = info.getThumbnail();
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

    private void setImage(final ImageInfo info, final WeakReference<ImageView> weakImage) {
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

    private void addViewToMap(ImageInfo info, WeakReference<ImageView> weakImage) {
        List<Pair<ImageInfo, WeakReference<ImageView>>> list = mappedViews.get(info.url);
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
                ImageInfo info = null;
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

                List<Pair<ImageInfo, WeakReference<ImageView>>> viewList = Collections.emptyList();
                synchronized (mappedViews) {
                    if (mappedViews.containsKey(info.url)) {
                        viewList = mappedViews.remove(info.url);
                    }
                }
                for (Pair<ImageInfo, WeakReference<ImageView>> pair : viewList) {
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

        private final ImageInfo info;

        public DownloadRunnable(ImageInfo info) {
            this.info = info;
        }

        @Override
        public void run() {
            imageLoadTask(info);
        }

    }

    private class AssignRunnable implements Runnable {

        private final ImageInfo info;
        private final ImageView iv;

        public AssignRunnable(ImageInfo info, ImageView iv) {
            this.info = info;
            this.iv = iv;
        }

        @Override
        public void run() {
            assignImage(iv, info);
            nextTask();
        }

    }

    private void imageLoadTask(final ImageInfo info) {
        internalLoadImage(info, new FileDownloadListener() {

            @Override
            public void onSuccess(int statusCode, File file) {
                List<Pair<ImageInfo, WeakReference<ImageView>>> viewList = Collections.emptyList();
                synchronized (mappedViews) {
                    if (mappedViews.containsKey(info.url)) {
                        viewList = mappedViews.remove(info.url);
                    }
                }

                if (info.loadListener != null) {
                    info.loadListener.onSuccess(statusCode, file);
                }

                for (Pair<ImageInfo, WeakReference<ImageView>> pair : viewList) {
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
                if (info.loadListener != null) {
                    info.loadListener.onProgress(progress);
                }
            }

            @Override
            public void onError(int statusCode) {
                w("[%s]Download failed: %s", statusCode, info.url);
                notifyImageReady(info, null);

                List<Pair<ImageInfo, WeakReference<ImageView>>> viewList = Collections.emptyList();
                synchronized (mappedViews) {
                    if (mappedViews.containsKey(info.url)) {
                        viewList = mappedViews.remove(info.url);
                    }
                }
                for (Pair<ImageInfo, WeakReference<ImageView>> pair : viewList) {
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

    private void assignImage(final ImageView view, final ImageInfo info) {
        File file = getImageFile(info);
        Bitmap image;
        if (view == null) {
            notifyImageReady(info, null);
            return;
        } else if (file == null) {
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
            image = ImageBlur.blur(ctx, image, info.blur);
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
                    if (info.exitAnimation != null) {
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

    private void setAssignFailResource(final ImageView view, final ImageInfo info) {
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

    private void notifyImageReady(ImageInfo info, Bitmap image) {
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

    /**
     * Checks does the local file of the image.
     *
     * @param info Image information.
     * @return true if local file exists and false is no file exist.
     */
    public boolean hasImage(ImageInfo info) {
        File f = getImageFile(info);
        return f != null && f.exists();
    }

    /**
     * Delete all images downloaded with ImageService.
     */
    public void deleteFiles() {
        downloadPool.execute(new Runnable() {
            @Override
            public void run() {
                FileUtils.deleteFiles(directory);
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
                FileUtils.deleteFilesOlderThan(directory, timeModified);
            }
        });
    }

    /**
     * Set correct rotation to {@link ImageView} based on given files
     * {@link ExifInterface} data.
     * <p/>
     * NOTE: This method only works after API 11.
     *
     * @param iv   ImageView that the rotation will be applied to
     * @param file Image file the exif data will be loaded from
     */
    public void checkExifRotation(ImageView iv, File file) {
        int rotation = getExifRotation(file);
        iv.setRotation(rotation);
    }

    /**
     * Get image rotation information from {@link ExifInterface} data from given
     * {@link File}.
     *
     * @param file Image file the exif data will be loaded from
     * @return Image rotation
     */
    public int getExifRotation(File file) {
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(file.getAbsolutePath());
        } catch (IOException e) {
            e(e, "Unable to get exif data");
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

    public int getMaxTextureSize() {
        return maxTextureSize;
    }

}