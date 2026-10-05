# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /work
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2,sharing=locked sh -c 'for attempt in 1 2 3; do mvn -B -q -Dmaven.wagon.http.retryHandler.count=5 dependency:go-offline && exit 0; sleep $((attempt * 5)); done; exit 1'
COPY src src
RUN --mount=type=cache,target=/root/.m2,sharing=locked sh -c 'TEST_ADMIN_PASSWORD="Aa9$(cat /proc/sys/kernel/random/uuid)" mvn -B -Dmaven.wagon.http.retryHandler.count=5 spotless:check clean package'
FROM maven:3.9-eclipse-temurin-21
RUN groupadd -g 10001 linenflow && useradd -u 10001 -g linenflow -m linenflow
WORKDIR /app
COPY --from=build --chown=linenflow:linenflow /work/target/zhuatech-linenflow.jar app.jar
USER linenflow
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
