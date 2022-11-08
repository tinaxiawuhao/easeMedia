# 多阶段构建
FROM aurorxa/maven-oracle-jdk8:3.8.6 as builder
WORKDIR /app
COPY . .
RUN cd /app && mvn -Dmaven.test.skip=true clean package
FROM aurorxa/oracle-jdk:8
RUN mkdir -pv /emdata
COPY --from=builder /app/emdata/* /emdata
COPY --from=builder /app/target/*.jar /app.jar
RUN yum -y install libxcb libx11-xcb1 libxss1 libasound2 libxkbfile1 alsa-lib-devel \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime  \
    && echo 'Asia/Shanghai' >/etc/timezone  \
    && touch /app.jar
# 环境变量
# docker run -e JAVA_OPTS="-Xmx512m -Xms64m" -e PARAMS="--spring.profiles.active=dev --server.port=8080" xxx
ENV JAVA_OPTS=""
ENV PARAMS=""
EXPOSE 8866
EXPOSE 8888
# 运行 jar 包
ENTRYPOINT [ "sh", "-c", "java -Djava.security.egd=file:/dev/./urandom $JAVA_OPTS -jar /app.jar $PARAMS" ]
