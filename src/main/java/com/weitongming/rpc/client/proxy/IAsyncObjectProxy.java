package com.weitongming.rpc.client.proxy;

import com.weitongming.rpc.client.RPCFuture;

/**
 * 异步调用接口
 * Created by tim.wei on 2017/3/16.
 */
public interface IAsyncObjectProxy {
    public RPCFuture call(String funcName, Object... args);
}