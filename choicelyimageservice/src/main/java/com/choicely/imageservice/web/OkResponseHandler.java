package com.choicely.imageservice.web;


import com.choicely.imageservice.log.ChoicelyLogService;

import java.io.Closeable;

import okhttp3.Response;

/**
 * Created by Tommy on 03/06/15.
 */
public abstract class OkResponseHandler<T> extends ChoicelyLogService {

    public OkResponseHandler() {
        super();
    }

    public OkResponseHandler(String tag) {
        super(tag);
    }

    public abstract T handleResponse(Response response);

    public abstract void closeOpenResources();

}
