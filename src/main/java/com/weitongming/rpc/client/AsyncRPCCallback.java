package com.weitongming.rpc.client;

/**
 * 接口回调
 * Created by tim.wei on 2017-03-17.
 */
public interface AsyncRPCCallback {

    void success(Object result);

    void fail(Exception e);

}
