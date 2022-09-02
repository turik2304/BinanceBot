package com.binance.connector.client.utils;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

public final class HttpClientSingleton {

    private static final Dispatcher dispatcher = new Dispatcher();

    private static final OkHttpClient httpClient = new OkHttpClient.Builder().dispatcher(getDispatcher()).build();

    private HttpClientSingleton() {}

    private static Dispatcher getDispatcher() {
        dispatcher.setMaxRequests(400);
        return dispatcher;
    }

    public static OkHttpClient getHttpClient() {
        return httpClient;
    }
}
