package com.weitongming.rpc.client.proxy;

import com.weitongming.rpc.client.ConnectManage;
import com.weitongming.rpc.client.RPCFuture;
import com.weitongming.rpc.client.RpcClientHandler;
import com.weitongming.rpc.protocol.RpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 泛型代理对象 proxyObject 用于发起netty连接
 * Created by tim.wei on 2017-03-16.
 */
public class ObjectProxy<T> implements InvocationHandler, IAsyncObjectProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectProxy.class);
    //class集合，用于获取代理类对象
    private Class<T> clazz;

    //构造函数传入，在spring之中传入
    public ObjectProxy(Class<T> clazz) {
        this.clazz = clazz;
    }

    //复写invocationHandler的invoke方法
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //获取方法所属类
        if (Object.class == method.getDeclaringClass()) {
            //获取方法名
            String name = method.getName();
            //基础方法
            if ("equals".equals(name)) {
                return proxy == args[0];
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            } else if ("toString".equals(name)) {
                return proxy.getClass().getName() + "@" +
                        Integer.toHexString(System.identityHashCode(proxy)) +
                        ", with InvocationHandler " + this;
            } else {
                throw new IllegalStateException(String.valueOf(method));
            }
        }
        //RpcRequest
        RpcRequest request = new RpcRequest();
        //设置id
        request.setRequestId(UUID.randomUUID().toString());
        //设置类名
        request.setClassName(method.getDeclaringClass().getName());
        //设置方法名
        request.setMethodName(method.getName());
        //设置参数类型
        request.setParameterTypes(method.getParameterTypes());
        //设置参数列表
        request.setParameters(args);
        // Debug
        LOGGER.debug(method.getDeclaringClass().getName());
        LOGGER.debug(method.getName());
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            LOGGER.debug(method.getParameterTypes()[i].getName());
        }
        //修复代理参数为空时出现的空指针异常 fixbug
        if (args != null)
        {
            for (int i = 0; i < args.length; ++i) {
                LOGGER.debug(args[i].toString());
            }
        }
        //获取可用处理机
        RpcClientHandler handler = ConnectManage.getInstance().chooseHandler();
        //发送请求
        RPCFuture rpcFuture = handler.sendRequest(request);
        //返回远程调用结果
        return rpcFuture.get();
    }
    //异步调用
    @Override
    public RPCFuture call(String funcName, Object... args) {
        RpcClientHandler handler = ConnectManage.getInstance().chooseHandler();
        RpcRequest request = createRequest(this.clazz.getName(), funcName, args);
        RPCFuture rpcFuture = handler.sendRequest(request);
        return rpcFuture;
    }
    //创建request
    private RpcRequest createRequest(String className, String methodName, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(className);
        request.setMethodName(methodName);
        request.setParameters(args);

        Class[] parameterTypes = new Class[args.length];
        // Get the right class type
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = getClassType(args[i]);
        }
        request.setParameterTypes(parameterTypes);
//        Method[] methods = clazz.getDeclaredMethods();
//        for (int i = 0; i < methods.length; ++i) {
//            // Bug: if there are 2 methods have the same name
//            if (methods[i].getName().equals(methodName)) {
//                parameterTypes = methods[i].getParameterTypes();
//                request.setParameterTypes(parameterTypes); // get parameter types
//                break;
//            }
//        }

        LOGGER.debug(className);
        LOGGER.debug(methodName);
        //修复代理参数为空时出现的空指针异常 fixbug
        if (parameterTypes != null) {
            for (int i = 0; i < parameterTypes.length; ++i) {
                LOGGER.debug(parameterTypes[i].getName());
            }
        }
        if (args != null)
            for (int i = 0; i < args.length; ++i) {
                LOGGER.debug(args[i].toString());
            }

        return request;
    }

    private Class<?> getClassType(Object obj){
        Class<?> classType = obj.getClass();
        String typeName = classType.getName();
        switch (typeName){
            case "java.lang.Integer":
                return Integer.TYPE;
            case "java.lang.Long":
                return Long.TYPE;
            case "java.lang.Float":
                return Float.TYPE;
            case "java.lang.Double":
                return Double.TYPE;
            case "java.lang.Character":
                return Character.TYPE;
            case "java.lang.Boolean":
                return Boolean.TYPE;
            case "java.lang.Short":
                return Short.TYPE;
            case "java.lang.Byte":
                return Byte.TYPE;
        }

        return classType;
    }

}
