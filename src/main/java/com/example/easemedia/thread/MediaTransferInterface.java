package com.example.easemedia.thread;

public interface MediaTransferInterface {

    /**
     * 监听客户端
     */
    void listenClient();

    /**
     * 检测客户端，关闭流
     */
    void hasClient();

    /**
     * 发送帧数据
     *
     * @param data 发送数据
     */
    void sendFrameData(byte[] data);

    /**
     * 启动
     */
    void execute();
}
