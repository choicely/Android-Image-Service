package com.choicely.imagecompare;

import android.app.Application;

import com.choicely.imageservice.ChoicelyImageService;
import com.choicely.imageservice.ChoicelyImageService.FileSystem;

import com.choicely.imagecompare.util.ImageUtil;
import com.choicely.imagecompare.util.QLog;

/**
 * Created by Tommy on 20/01/16.
 */
public class ImageCompareApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        QLog.setDebug(BuildConfig.DEBUG);

        initImageService();
        ImageUtil.init(this);
    }

    private void initImageService() {
        ChoicelyImageService.init(this, FileSystem.APPLICATION_CACHE, "images/", 5, 5);
        ChoicelyImageService is = ChoicelyImageService.getInstance();
//        is.setDebug(true);
        is.setDebug(false);
    }
}
