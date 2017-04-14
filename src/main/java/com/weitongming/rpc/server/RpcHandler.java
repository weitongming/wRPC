package com.weitongming.rpc.server;

import com.weitongming.rpc.protocol.RpcRequest;
import com.weitongming.rpc.protocol.RpcResponse;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * RPC Handler（RPC request processor）
 * @author tim.wei
 */
public class RpcHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcHandler.class);

    private final Map<String, Object> handlerMap;

    public RpcHandler(Map<String, Object> handlerMap) {
        this.handlerMap = handlerMap;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx,final RpcRequest request) throws Exception {
        //调用RpcServer的静态方法
        RpcServer.submit(new Runnable() {
            public void run() {
                LOGGER.debug("接收到请求：" + request.getRequestId());
                RpcResponse response = new RpcResponse();
                response.setRequestId(request.getRequestId());

                try {
                    //调用handle方法处理请求
                    Object result = handle(request);
                    //存入请求结果
                    response.setResult(result);
                } catch (Throwable t) {
                    response.setError(t.toString());
                    LOGGER.error("RPC Server 处理请求出错",t);
                }
                //发送处理结果
                ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        LOGGER.debug("发送处理结果： " + request.getRequestId());
                    }
                });
            }
        });
    }
    //处理请求 反射
    private Object handle(RpcRequest request) throws Throwable {
        //取出类名并根据类名获取bean（bean已用注解将类名绑定）
        String className = request.getClassName();
        Object serviceBean = handlerMap.get(className);

        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();

        LOGGER.debug("获取到的类名" + serviceClass.getName());
        LOGGER.debug("获取到的方法名" + methodName);
        //修复空指针bug
        if (parameters != null){
            for (int i = 0; i < parameterTypes.length; ++i) {
                LOGGER.debug(parameterTypes[i].getName());
            }
            for (int i = 0; i < parameters.length; ++i) {
                LOGGER.debug(parameters[i].toString());
            }
        }


        // JDK reflect
        /*Method method = serviceClass.getMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(serviceBean, parameters);*/

        // Cglib reflect
        FastClass serviceFastClass = FastClass.create(serviceClass);
        FastMethod serviceFastMethod = serviceFastClass.getMethod(methodName, parameterTypes);
        // TODO: 2017/4/10 未加入类似线程池

        //方法.Invoke （实例化的bean，参数）
        return serviceFastMethod.invoke(serviceBean, parameters);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("rpc服务捕捉到以异常", cause);
        ctx.close();
    }
}
