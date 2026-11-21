# 绿益公益后台 · 微信云托管 Dockerfile
# 构建上下文：server/ 目录（即 Dockerfile 所在目录）
# 端口：容器监听 8080，与 application.yml / application-cloud.yml 的 server.port 一致；
#       云托管控制台「端口」必须填 8080（不是默认的 80）。
# 配置：JAVA_OPTS 中内置 -Dspring.profiles.active=cloud，云端强制走 application-cloud.yml，
#       否则会退回到 application.yml 里的本地数据库地址 192.168.0.102 导致启动失败。

# ---------- 阶段一：编译打包 ----------
FROM maven:3.9-eclipse-temurin-8 AS builder
WORKDIR /build
# 用阿里云镜像加速依赖下载，避免云托管构建机拉中央仓库慢/超时
RUN mkdir -p /root/.m2 && printf '%s\n' \
  '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
  '  <mirrors><mirror><id>aliyun</id><mirrorOf>*</mirrorOf>' \
  '  <url>https://maven.aliyun.com/repository/public</url></mirror></mirrors>' \
  '</settings>' > /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline || true
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -XX:+UseG1GC -Dspring.profiles.active=cloud"
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && mkdir -p /app/uploads
COPY --from=builder /build/target/charity-miniprogram-server-1.0.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
