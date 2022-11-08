
# EasyMedia

#### 介绍
Springboot、netty实现的http-flv、websocket-flv流媒体服务（可用于直播点播），支持rtsp、h264、h265等、rtmp等多种源，h5纯js播放（不依赖flash），不需要依赖nginx等第三方，低延迟（支持识别h264、aac编码自动转封装）。


#### 构建 基于 Oracle-jdk 8 的 Maven 镜像

```dockerfile

FROM centos:7.9.2009
# java
ARG JAVA_VERSIOIN=1.8.0
SHELL ["/bin/bash", "-c"]
ENV BASH_ENV ~/.bashrc
ENV JAVA_HOME /usr/local/jdk-${JAVA_VERSIOIN}
ENV PATH ${JAVA_HOME}/bin:$PATH

RUN \
  # Install JDK
  if [ "$JAVA_VERSIOIN" == "1.8.0" ]; \
  then \
    yum -y remove java-1.8.0-openjdk \
    && curl -fSL https://files-cdn.liferay.com/mirrors/download.oracle.com/otn-pub/java/jdk/8u121-b13/jdk-8u121-linux-x64.tar.gz -o openjdk.tar.gz \
    && mkdir -pv /usr/local/jdk-1.8.0 && tar -zxvf openjdk.tar.gz -C /usr/local/jdk-1.8.0 --strip-components 1 \
    && rm -f openjdk.tar.gz \
    && echo "export JAVA_HOME=/usr/local/jdk-${JAVA_VERSIOIN}" >> ~/.bashrc \
    && echo "export PATH=\"/usr/local/jdk-${JAVA_VERSIOIN}/bin:$PATH\"" >> ~/.bashrc \
    && echo "export JAVA_HOME PATH " >> ~/.bashrc  \
    && cat ~/.bashrc  \
    && source ~/.bashrc ; \
  fi \
    # Test install
    && ls -l /usr/local/ \
    && javac -version

ARG MAVEN_VERSION=3.5.3
ENV M2_HOME /opt/apache-maven-$MAVEN_VERSION
ENV JAVA_HOME /usr/local/jdk-${JAVA_VERSIOIN}
ENV maven.home $M2_HOME
ENV M2 $M2_HOME/bin
ENV PATH $M2:$PATH:JAVA_HOME/bin
RUN curl -f -L https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar -C /opt -xzv  \
    && rm -f gradle.zip  \
    && echo "export M2_HOME=/opt/apache-maven-${MAVEN_VERSION}" >> ~/.bashrc  \
    && echo "export MAVEN_HOME=${M2_HOME}" >> ~/.bashrc  \
    && echo "export M2=${M2_HOME}/bin" >> ~/.bashrc  \
    && echo "export PATH=\"$M2:$PATH:JAVA_HOME/bin\"" >> ~/.bashrc  \
    && echo "export M2_HOME MAVEN_HOME M2 PATH " >> ~/.bashrc  \
    && cat ~/.bashrc  \
    && source ~/.bashrc \
    && ls -l /opt \
    && mvn -v \

CMD ["mvn","-version"]

```


#### 功能汇总 （不知道怎么使用的可以直接看wiki，简洁明了）
- 支持播放 rtsp、rtmp、http、文件等流……
- pc端桌面投影
- 支持永久播放、按需播放（无人观看自动断开）
- 自动判断流格式h264、h265，自动转封装
- 支持http、ws协议的flv
- 支持hls内存切片（不占用本地磁盘，只占用网络资源）
- 重连功能
- 支持javacv、ffmpeg方式切换


#### 软件架构
- netty负责播放地址解析及视频传输，通过javacv推拉流存到内存里，直接通过输出到前端播放
- 后端：springboot、netty，集成websocket
- 前端：vue、html5（简单的管理页面）
- 媒体框架：javacv、ffmpeg


#### 使用教程
> 流媒体服务会绑定两个端口，分别为 8866（媒体端口）、8888（web端口，后续会做简单的管理页面）
您只需要将 {您的源地址} 替换成您的，然后放播放器里就能看了


- 播放地址（播放器里直接用这个地址播放）
```
http://localhost:8866/live?url={您的源地址}
ws://localhost:8866/live?url={您的源地址}
```

#### 疑问解答
- 在vlc、ffplay等播放器测试存在延迟较高是正常的，是因为他们默认的嗅探关键帧的时间比较长，测延迟建议还是用flv.js播放器测试。
- 是否需要ffmpeg推流，不需要，就是为了简化使用，只需运行一个服务即可。
- 很多人想用文件点播，可以参考截图（目前对文件播放未做优化，可以参考）。


