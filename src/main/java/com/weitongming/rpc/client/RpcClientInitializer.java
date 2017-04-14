package com.weitongming.rpc.client;

import com.weitongming.rpc.protocol.RpcDecoder;
import com.weitongming.rpc.protocol.RpcEncoder;
import com.weitongming.rpc.protocol.RpcRequest;
import com.weitongming.rpc.protocol.RpcResponse;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * Created by tim.wei on 2017-03-16.
 */
public class RpcClientInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline cp = socketChannel.pipeline();
        //编码
        cp.addLast(new RpcEncoder(RpcRequest.class));
        //解码
        cp.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0));
        //解码
        cp.addLast(new RpcDecoder(RpcResponse.class));
        //客户端 ---最底层的发送
        cp.addLast(new RpcClientHandler());
    }
}
