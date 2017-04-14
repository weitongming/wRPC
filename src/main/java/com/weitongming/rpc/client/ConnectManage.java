package com.weitongming.rpc.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * zookeeper rpc链接管理
 * Created by tim.wei on 2017-03-16.
 */
public class ConnectManage {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectManage.class);
    //高并发同步
    private volatile static ConnectManage connectManage;
    //事件循环
    EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);
    //线程池
    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor
            (16, 16, 600L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(65536));
    //高并发同步RpcClientHandler集合
    private CopyOnWriteArrayList<RpcClientHandler> connectedHandlers = new CopyOnWriteArrayList<>();
    //高并发同步InetSocketAddress 、RpcClientHandler的map
    private Map<InetSocketAddress, RpcClientHandler> connectedServerNodes = new ConcurrentHashMap<>();
    //private Map<InetSocketAddress, Channel> connectedServerNodes = new ConcurrentHashMap<>();
    //可重入锁，激烈争用情况下更佳的性能 ！！！！！务必记得释放锁
    private ReentrantLock lock = new ReentrantLock();
    private Condition connected = lock.newCondition();
    protected long connectTimeoutMillis = 6000;
    //线程安全的加减操作接口
    private AtomicInteger roundRobin = new AtomicInteger(0);
    private volatile boolean isRuning = true;
    //私有构造函数
    private ConnectManage() {
    }
    //单例
    public static ConnectManage getInstance() {
        if (connectManage == null) {
            synchronized (ConnectManage.class) {
                if (connectManage == null) {
                    connectManage = new ConnectManage();
                }
            }
        }
        return connectManage;
    }
    //更新已连接服务
    public void updateConnectedServer(List<String> allServerAddress) {
        if (allServerAddress != null) {
            //获取所有可用服务
            if (allServerAddress.size() > 0) {
                //update local serverNodes cache
                HashSet<InetSocketAddress> newAllServerNodeSet = new HashSet<InetSocketAddress>();
                //遍历zk
                for (int i = 0; i < allServerAddress.size(); ++i) {
                    //拆分
                    String[] array = allServerAddress.get(i).split(":");
                    //获取ip和端口
                    if (array.length == 2) { // Should check IP and port
                        String host = array[0];
                        int port = Integer.parseInt(array[1]);
                        final InetSocketAddress remotePeer = new InetSocketAddress(host, port);
                        //往新的服务节点列表添加
                        newAllServerNodeSet.add(remotePeer);
                    }
                }

                // 添加新的服务节点
                for (final InetSocketAddress serverNodeAddress : newAllServerNodeSet) {
                    //遍历新的服务节点列表，不存在
                    if (!connectedServerNodes.keySet().contains(serverNodeAddress)) {
                        //链接服务节点
                        connectServerNode(serverNodeAddress);
                    }
                }

                // 遍历新的服务节点列表并清除无效节点
                for (int i = 0; i < connectedHandlers.size(); ++i) {
                    RpcClientHandler connectedServerHandler = connectedHandlers.get(i);
                    SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
                    //如果新的服务节点之中已经不存在节点
                    if (!newAllServerNodeSet.contains(remotePeer)) {
                        LOGGER.info("远程节点已无效：" + remotePeer);
                        RpcClientHandler handler = connectedServerNodes.get(remotePeer);
                        handler.close();
                        connectedServerNodes.remove(remotePeer);
                        connectedHandlers.remove(connectedServerHandler);
                    }
                }

            } else { // No available server node ( All server nodes are down )
                LOGGER.error("没有可用的服务节点，所有的服务节点都已宕机！！");
                for (final RpcClientHandler connectedServerHandler : connectedHandlers) {
                    SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
                    RpcClientHandler handler = connectedServerNodes.get(remotePeer);
                    handler.close();
                    connectedServerNodes.remove(connectedServerHandler);
                }
                connectedHandlers.clear();
            }
        }
    }
    //重新连接
    public void  reconnect(final RpcClientHandler handler, final SocketAddress remotePeer){
        //先移除
        if(handler!=null){
            connectedHandlers.remove(handler);
            connectedServerNodes.remove(handler.getRemotePeer());
        }
        connectServerNode((InetSocketAddress)remotePeer);
    }

    private void connectServerNode(final InetSocketAddress remotePeer) {
        threadPoolExecutor.submit(new Runnable() {
            @Override
            public void run() {
                Bootstrap b = new Bootstrap();
                b.group(eventLoopGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new RpcClientInitializer());
                //通过bootstrap连接远程节点并返回Channel异步操作的结果
                ChannelFuture channelFuture = b.connect(remotePeer);
                channelFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                        if (channelFuture.isSuccess()) {
                            LOGGER.debug("连接到远程节点成功，远程节点为 = " + remotePeer);
                            //连接成功后获取handler处理机
                            RpcClientHandler handler = channelFuture.channel().pipeline().get(RpcClientHandler.class);
                            //添加处理机
                            addHandler(handler);
                        }
                    }
                });
            }
        });
    }
    //添加处理机
    private void addHandler(RpcClientHandler handler) {
        connectedHandlers.add(handler);
        InetSocketAddress remoteAddress = (InetSocketAddress) handler.getChannel().remoteAddress();
        connectedServerNodes.put(remoteAddress, handler);
        signalAvailableHandler();
    }

    private void signalAvailableHandler() {
        lock.lock();
        //唤醒在此Lock对象上等待的所有线程
        try {
            connected.signalAll();
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    private boolean waitingForHandler() throws InterruptedException {
        lock.lock();
        try {
            return connected.await(this.connectTimeoutMillis, TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }

    public RpcClientHandler chooseHandler() {
        //写时复制的容器，用于高并发时读远高于写的情况
        CopyOnWriteArrayList<RpcClientHandler> handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlers.clone();
        int size = handlers.size();
        while (isRuning && size <= 0) {
            try {
                //等待可冲入锁
                boolean available = waitingForHandler();
                if (available) {
                    handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlers.clone();
                    size = handlers.size();
                }
            } catch (InterruptedException e) {
                LOGGER.error("Waiting for available node is interrupted! ", e);
                throw new RuntimeException("Can't connect any servers!", e);
            }
        }
        //返回处理机
        int index = (roundRobin.getAndAdd(1) + size) % size;
        return handlers.get(index);
    }
    //关闭
    public void stop(){
        isRuning = false;
        for (int i = 0; i < connectedHandlers.size(); ++i) {
            RpcClientHandler connectedServerHandler = connectedHandlers.get(i);
            connectedServerHandler.close();
        }
        signalAvailableHandler();
        threadPoolExecutor.shutdown();
        //优雅退出
        eventLoopGroup.shutdownGracefully();
    }
}
