package com.weitongming.rpc.registry;

/**
 * ZooKeeper 常量
 *
 * @author tim.wei
 */
public interface Constant {

    int ZK_SESSION_TIMEOUT = 5000;

    String ZK_REGISTRY_PATH = "/registry";
    String ZK_DATA_PATH = ZK_REGISTRY_PATH + "/data";
}
