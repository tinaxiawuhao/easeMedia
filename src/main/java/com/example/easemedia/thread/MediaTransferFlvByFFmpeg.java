package com.example.easemedia.thread;

import cn.hutool.core.collection.CollUtil;
import com.example.easemedia.common.MediaConstant;
import com.example.easemedia.entity.dto.CameraDto;
import com.example.easemedia.service.MediaService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 使用ffmpeg推拉流，可以说无敌了
 * <p>
 * 优点：支持各种杂七杂八的流，兼容性比较好，稳定，不容易出错，自身带有重连机制，可以自己使用命令封装 缺点：系统会存在多个ffmpeg进程,
 * 无法直接操作帧，延迟优化没javacv方便
 */
@Slf4j
public class MediaTransferFlvByFFmpeg extends MediaFFmpegTransfer {

    //socket通道
    public ServerSocket tcpServer = null;
    //命令存储
    public final List<String> command = new ArrayList<>();

    public MediaTransferFlvByFFmpeg(final String executable) {
        command.add(executable);
        buildCommand();
    }

    public MediaTransferFlvByFFmpeg(CameraDto cameraDto) {
        command.add(System.getProperty(MediaConstant.ffmpegPathKey));
        this.cameraDto = cameraDto;
        buildCommand();
    }

    public MediaTransferFlvByFFmpeg(final String executable, CameraDto cameraDto) {
        command.add(executable);
        this.cameraDto = cameraDto;
        buildCommand();
    }

    public MediaTransferFlvByFFmpeg(final String executable, CameraDto cameraDto, boolean enableLog) {
        command.add(executable);
        this.cameraDto = cameraDto;
        this.enableLog = enableLog;
        buildCommand();
    }

    public boolean isEnableLog() {
        return enableLog;
    }

    public void setEnableLog(boolean enableLog) {
        this.enableLog = enableLog;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    private MediaTransferFlvByFFmpeg addArgument(String argument) {
        command.add(argument);
        return this;
    }

    /**
     * 1.5.6开始javacv不再默认包含gpl许可协议的编解码库，涉及到的库包含：libx264、libx265等等。
     * 关于gpl许可协议提示
     * 商用软件如果确认不开源，请谨慎考虑是否使用gpl许可的代码库。
     * 使用h264在默认情况下，ffmpeg会使用cisco(思科)的openh264编解码库，所以h264也不受影响，除非你要使用libx264，则必须在原有基础上添加下述依赖项。
     * -- Optional GPL builds with (almost) everything enabled
     *         <dependency>
     *             <groupId>org.bytedeco</groupId>
     *             <artifactId>ffmpeg-platform-gpl</artifactId>
     *             <version>4.4-1.5.6</version>
     *         </dependency>
     * -----------------------------------
     *
     * JavaCV升级1.5.6之后遇到h265/hevc编码的视频无法打开编解码器avcodec_open2() error -1:Could not open video codec异常解决办法
     * https://blog.51cto.com/eguid/4859610
     * 构建ffmpeg转码命令,1.56新版javacv移除libx264，使用libopenh264 查看显卡硬件加速支持的选项ffmpeg -hwaccels
     * 查看ffmpeg支持选项 linux：ffmpeg -codecs | grep cuvid， window：ffmpeg -codecs |
     * findstr cuvid h264_nvenc ffmpeg -hwaccel cuvid -c:v h264_cuvid
     * -rtsp_transport tcp -i "rtsp地址" -c:v h264_nvenc -b:v 500k -vf
     * scale_npp=1280:-1 -y /home/2.mp4
     * <p>
     * -hwaccel cuvid：指定使用cuvid硬件加速 -c:v h264_cuvid：使用h264_cuvid进行视频解码 -c:v
     * h264_nvenc：使用h264_nvenc进行视频编码 -vf
     * scale_npp=1280:-1：指定输出视频的宽高，注意，这里和软解码时使用的-vf scale=x:x不一样
     * <p>
     * 转码期间nvidia-smi查看显卡状态 -hwaccel_device N 指定某颗GPU执行转码任务
     */
    private void buildCommand() {
        // 如果为rtsp流，增加配置
        if ("rtsp".equals(cameraDto.getUrl().substring(0, 4))) {
            this.addArgument("-rtsp_transport").addArgument("tcp");
        }
        //如果是本地文件
        if (cameraDto.getType() == 1) {
            this.addArgument("-re");
        }

        this.addArgument("-i").addArgument(cameraDto.getUrl())
                .addArgument("-max_delay").addArgument("1")
//		.addArgument("-strict").addArgument("experimental")
                .addArgument("-g").addArgument("25").addArgument("-r").addArgument("25")
//		.addArgument("-b").addArgument("200000")
//		.addArgument("-filter_complex").addArgument("setpts='(RTCTIME - RTCSTART) / (TB * 1000000)'")
                .addArgument("-c:v").addArgument("libopenh264").addArgument("-preset:v").addArgument("ultrafast")
//		.addArgument("-preset:v").addArgument("fast")
                .addArgument("-tune:v").addArgument("zerolatency")
//		.addArgument("-crf").addArgument("26")
                .addArgument("-c:a").addArgument("aac")
//		.addArgument("-qmin").addArgument("28")
//		.addArgument("-qmax").addArgument("32")
//		.addArgument("-b:v").addArgument("448k")
//		.addArgument("-b:a").addArgument("64k")
                .addArgument("-f").addArgument("flv");
    }


    /**
     * 执行推流
     *
     * @return
     */
    @SneakyThrows
    public void execute() {
        command.add(getOutput());
        log.info(CollUtil.join(command, " "));
        process = new ProcessBuilder(command).start();
        running = true;
        listenNetTimeout();
        dealStream(process);
        outputData();
        listenClient();
    }

    /**
     * flv数据
     */
    private void outputData() {
        CompletableFuture.runAsync(() -> {
            try (Socket client = tcpServer.accept()) {
                DataInputStream input = new DataInputStream(client.getInputStream());
                byte[] buffer = new byte[1024];
                int len;
                log.info("running...."+ running);
                while (running) {
                    len = input.read(buffer);
                    if (len == -1) {
                        break;
                    }
                    bos.write(buffer, 0, len);
                    if (header == null) {
                        header = bos.toByteArray();
                        bos.reset();
                        continue;
                    }

                    // 帧数据
                    byte[] data = bos.toByteArray();
                    bos.reset();
                    // 发送到前端
                    sendFrameData(data);
                }

                try {
                    client.close();
                    input.close();
                    bos.close();
                } catch (java.lang.Exception ignored) {
                }

                log.info("关闭媒体流-ffmpeg，{} ", cameraDto.getUrl());

            } catch (IOException ignored) {
            } finally {
                MediaService.cameras.remove(cameraDto.getMediaKey());
                running = false;
                process.destroy();
                try {
                    if (null != tcpServer) {
                        tcpServer.close();
                    }
                } catch (IOException ignored) {
                }
            }
        });
    }


    /**
     * 监听网络异常超时
     */
    public void listenNetTimeout() {
        CompletableFuture.runAsync(() -> {
                    while (true) {
                        if ((System.currentTimeMillis() - currentTimeMillis) > 15000) {
                            log.info("网络异常超时");
                            MediaService.cameras.remove(cameraDto.getMediaKey());
                            stopFFmpeg();
                            break;
                        }

                        try {
                            TimeUnit.MILLISECONDS.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
        );
    }

    public static MediaTransferFlvByFFmpeg atPath() {
        return atPath(null);
    }

    public static MediaTransferFlvByFFmpeg atPath(final String absPath) {
        final String executable;
        if (absPath != null) {
            executable = absPath;
        } else {
//			executable = "ffmpeg";
            executable = System.getProperty(MediaConstant.ffmpegPathKey);
        }
        return new MediaTransferFlvByFFmpeg(executable);
    }



    /**
     * 输出到tcp
     *
     * @return tcp地址
     */
    private String getOutput() {
        try {
            tcpServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            tcpServer.setSoTimeout(10000);
            return "tcp://" +
                    tcpServer.getInetAddress().getHostAddress() +
                    ":" +
                    tcpServer.getLocalPort();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("无法启用端口");
        }
    }


    /**
     * 关闭
     */
    public void stopFFmpeg() {
        super.stop();
        // 媒体异常时，主动断开前端长连接
        super.stopFFmpeg();
    }




}
