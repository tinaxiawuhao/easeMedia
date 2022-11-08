package com.example.easemedia.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@ConfigurationProperties(prefix = "mediaserver")
@Component
public class MediaProperties {
    /**
     * 流媒体服务端口
     */
    private Integer port;
    /**
     * 版本号
     */
    private String version;
    /**
     * 网络超时，15秒
     */
    private String netTimeout;
    /**
     * 读写超时，15秒
     */
    private String readOrWriteTimeout;
    /**
     * 保存录像切片目录
     */
    private String path;
    /**
     * 无人观看时是否自动关闭流
     */
    private Boolean autoClose;
    /**
     * 无人拉流观看持续多久自动关闭，1分钟
     */
    private Integer noClientsDuration;

}