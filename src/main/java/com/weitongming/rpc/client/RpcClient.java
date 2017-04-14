package com.weitongming.rpc.client;

import com.weitongming.rpc.client.proxy.IAsyncObjectProxy;
import com.weitongming.rpc.client.proxy.ObjectProxy;
import com.weitongming.rpc.registry.ServiceDiscovery;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * RPC Client（Create RPC proxy）
 * @author tim.wei
 */
public class RpcClient {

    private String serverAddress;
    private ServiceDiscovery serviceDiscovery;
    private Map<String,Object> proxys = new HashMap<>();
    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(16, 16, 600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65536));

    public RpcClient(String serverAddress) {
        this.serverAddress = serverAddress;
    }
    //
    public RpcClient(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }
    //构造函数，用于在spring配置文件之中构造代理对象
    public RpcClient(ServiceDiscovery serviceDiscovery, List<String> classList){
        this.serviceDiscovery = serviceDiscovery;
        for (String className:classList )
        {
            try {
                Class cls = Class.forName(className);
                proxys.put(className,Proxy.newProxyInstance(
                        cls.getClassLoader(),
                        new Class<?>[]{cls},
                        new ObjectProxy<>(cls)));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

        }
    }
    //通过服务名获取代理类对象
    public Object getClient(String serviceName)
    {
        return proxys.get(serviceName);
    }
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new ObjectProxy<T>(interfaceClass)
        );
    }

    public static <T> IAsyncObjectProxy createAsync(Class<T> interfaceClass) {
        return new ObjectProxy<T>(interfaceClass);
    }

    public static void submit(Runnable task){
        threadPoolExecutor.submit(task);
    }

    public void stop() {
        threadPoolExecutor.shutdown();
        serviceDiscovery.stop();
        ConnectManage.getInstance().stop();
    }
}

