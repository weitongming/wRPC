package com.weitongming.rpc.registry;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * 服务注册
 *
 * @author tim.wei
 */
public class ServiceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRegistry.class);
    //并行等待
    private CountDownLatch latch = new CountDownLatch(1);
    //注册地址
    private String registryAddress;
    //构造
    public ServiceRegistry(String registryAddress) {
        this.registryAddress = registryAddress;
    }
    //注册
    public void register(String data) {
        if (data != null) {
            ZooKeeper zk = connectServer();
            if (zk != null) {
                //如果zk不为空
                AddRootNode(zk); // Add root node if not exist
                createNode(zk, data);
            }
        }
    }
    //连接到zk并获取zk
    private ZooKeeper connectServer() {
        ZooKeeper zk = null;
        try {
            zk = new ZooKeeper(registryAddress, Constant.ZK_SESSION_TIMEOUT, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                        //直到连接成功才会计数器减一
                        latch.countDown();
                    }
                }
            });
            latch.await();
        } catch (IOException e) {
            LOGGER.error("zk IO 出现异常", e);
        }
        catch (InterruptedException ex){
            LOGGER.error("zk 中断异常", ex);
        }
        return zk;
    }
    //创建根节点
    private void AddRootNode(ZooKeeper zk){
        try {
            Stat s = zk.exists(Constant.ZK_REGISTRY_PATH, false);
            //如果根节点不存在 创建根节点
            if (s == null) {
                zk.create(Constant.ZK_REGISTRY_PATH, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (KeeperException e) {
            LOGGER.error(e.toString());
        } catch (InterruptedException e) {
            LOGGER.error(e.toString());
        }
    }
    //创建节点
    private void createNode(ZooKeeper zk, String data) {
        try {
            byte[] bytes = data.getBytes();
            //创建节点
            String path = zk.create(Constant.ZK_DATA_PATH, bytes, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            LOGGER.debug("创建 zookeeper 节点 ({} => {})", path, data);
        } catch (KeeperException e) {
            LOGGER.error("", e);
        }
        catch (InterruptedException ex){
            LOGGER.error("", ex);
        }
    }
}