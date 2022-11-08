package com.example.easemedia.thread;

import com.example.easemedia.common.ClientType;
import com.example.easemedia.entity.dto.CameraDto;
import com.example.easemedia.service.MediaService;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.FrameGrabber;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 媒体转换者
 */
@Slf4j
public abstract class MediaTransfer implements MediaTransferInterface {

    static {
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);
        FFmpegLogCallback.set();
    }

    //操作对象
    public CameraDto cameraDto;

    // 启动标识
    public boolean running = false;

    /**
     * 当前在线人数
     */
    public int hcSize, wcSize = 0;

    /**
     * 用于没有客户端时候的计时
     */
    public int noClient = 0;

    /**
     * flv header
     */
    public byte[] header = null;

    /**
     * ws客户端
     */
    public ConcurrentHashMap<String, ChannelHandlerContext> wsClients = new ConcurrentHashMap<>();

    /**
     * http客户端
     */
    public ConcurrentHashMap<String, ChannelHandlerContext> httpClients = new ConcurrentHashMap<>();


    /**
     * 输出流，视频最终会输出到此
     */
    public ByteArrayOutputStream bos = new ByteArrayOutputStream();


    /**
     * 监听客户端
     */
    public void listenClient() {
        CompletableFuture.runAsync(() -> {
                    while (running) {
                        hasClient();
                        try {
                            TimeUnit.MILLISECONDS.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                    }

                }
        );
    }

    /**
     * 检测客户端，关闭流
     */
    public void hasClient() {

        int newHcSize = httpClients.size();
        int newWcSize = wsClients.size();
        if (hcSize != newHcSize || wcSize != newWcSize) {
            hcSize = newHcSize;
            wcSize = newWcSize;
            log.info("\r\n{}\r\nhttp连接数：{}, ws连接数：{} \r\n", cameraDto.getUrl(), newHcSize, newWcSize);
        }

        // 无需自动关闭
        if (!cameraDto.isAutoClose()) {
            return;
        }

        if (httpClients.isEmpty() && wsClients.isEmpty()) {
            // 等待20秒还没有客户端，则关闭推流
            if (noClient > cameraDto.getNoClientsDuration()) {
                running = false;
                MediaService.cameras.remove(cameraDto.getMediaKey());
            } else {
                noClient += 1000;
//				log.info("\r\n{}\r\n {} 秒自动关闭推拉流 \r\n", camera.getUrl(), noClientsDuration-noClient);
            }
        } else {
            // 重置计时
            noClient = 0;
        }
    }

    /**
     * 发送帧数据
     *
     * @param data 发送数据
     */
    public void sendFrameData(byte[] data) {
        // ws
        for (Map.Entry<String, ChannelHandlerContext> entry : wsClients.entrySet()) {
            try {
                if (entry.getValue().channel().isWritable()) {
                    entry.getValue().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(data)));
                } else {
                    wsClients.remove(entry.getKey());
                    hasClient();
                }
            } catch (java.lang.Exception e) {
                wsClients.remove(entry.getKey());
                hasClient();
                e.printStackTrace();
            }
        }
        // http
        for (Map.Entry<String, ChannelHandlerContext> entry : httpClients.entrySet()) {
            try {
                if (entry.getValue().channel().isWritable()) {
                    entry.getValue().writeAndFlush(Unpooled.copiedBuffer(data));
                } else {
                    httpClients.remove(entry.getKey());
                    hasClient();
                }
            } catch (java.lang.Exception e) {
                httpClients.remove(entry.getKey());
                hasClient();
                e.printStackTrace();
            }
        }
    }

    /**
     * 关闭
     */
    public void stopFFmpeg() {
        // 媒体异常时，主动断开前端长连接
        for (Map.Entry<String, ChannelHandlerContext> entry : wsClients.entrySet()) {
            try {
                entry.getValue().close();
            } catch (java.lang.Exception ignored) {
            } finally {
                wsClients.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, ChannelHandlerContext> entry : httpClients.entrySet()) {
            try {
                entry.getValue().close();
            } catch (java.lang.Exception ignored) {
            } finally {
                httpClients.remove(entry.getKey());
            }
        }
    }

    /**
     * 新增客户端
     *
     * @param ctx   netty client
     * @param ctype enum,ClientType
     */
    @SneakyThrows
    public void addClient(ChannelHandlerContext ctx, ClientType ctype) {
        int timeout = 0;
        while (true) {
            if (Objects.nonNull(header)) {
                try {
                    if (ctx.channel().isWritable()) {
                        // 发送帧前先发送header
                        if (ClientType.HTTP.getType() == ctype.getType()) {
                            ChannelFuture future = ctx.writeAndFlush(Unpooled.copiedBuffer(header));
                            future.addListener(new GenericFutureListener<Future<? super Void>>() {
                                @Override
                                public void operationComplete(Future<? super Void> future) throws FrameGrabber.Exception {
                                    if (future.isSuccess()) {
                                        httpClients.put(ctx.channel().id().toString(), ctx);
                                    }
                                }
                            });
                        } else if (ClientType.WEBSOCKET.getType() == ctype.getType()) {
                            ChannelFuture future = ctx
                                    .writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(header)));
                            future.addListener(new GenericFutureListener<Future<? super Void>>() {
                                @Override
                                public void operationComplete(Future<? super Void> future) throws FrameGrabber.Exception {
                                    if (future.isSuccess()) {
                                        wsClients.put(ctx.channel().id().toString(), ctx);
                                    }
                                }
                            });
                        }
                    }

                } catch (java.lang.Exception e) {
                    e.printStackTrace();
                }
                break;
            }

            // 等待推拉流启动
            TimeUnit.MILLISECONDS.sleep(50);
            // 启动录制器失败
            timeout += 50;
            if (timeout > 30000) {
                break;
            }
        }
    }
}
