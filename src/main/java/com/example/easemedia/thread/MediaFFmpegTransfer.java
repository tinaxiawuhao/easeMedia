package com.example.easemedia.thread;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

/**
 * 媒体转换者
 */
@Slf4j
public abstract class MediaFFmpegTransfer extends MediaTransfer {


    // 记录当前
    long currentTimeMillis = System.currentTimeMillis();

    // 打印日志标识
    public boolean enableLog = true;

    //一个新进程
    public Process process;

    /**
     * 控制台输出
     *
     * @param process 进程
     */
    public void dealStream(Process process) {
        if (process == null) {
            return;
        }
        // 处理InputStream的线程
        CompletableFuture.runAsync(() -> {
                    BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    try {
                        while (running) {
                            line = in.readLine();
                            currentTimeMillis = System.currentTimeMillis();
                            if (line == null) {
                                break;
                            }
                            if (enableLog) {
                                log.info("output: " + line);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            running = false;
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
        );
        // 处理ErrorStream的线程
        CompletableFuture.runAsync(() -> {
            BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line = null;
            try {
                while (running) {
                    line = err.readLine();
                    currentTimeMillis = System.currentTimeMillis();
                    if (line == null) {
                        break;
                    }
                    if (enableLog) {
                        log.info("ffmpeg: " + line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    running = false;
                    err.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 关闭
     */
    public void stop() {
        this.running = false;
        try {
            this.process.destroy();
            log.info("关闭媒体流-ffmpeg，{} ", cameraDto.getUrl());
        } catch (java.lang.Exception e) {
            process.destroyForcibly();
        }
    }
}
