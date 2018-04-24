package com.choicely.imageservice;

import java.io.File;

public interface ImageLoadListener {

	void onSuccess(File file);

	void onFail(int statusCode);

	void onProgress(float progress);

}
