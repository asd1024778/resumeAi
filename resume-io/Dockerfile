# 后端镜像：默认官方库；国内可在 compose / .env 通过 ARG 覆盖镜像前缀
# 例：MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
ARG JRE_IMAGE=eclipse-temurin:17-jre-jammy

# ========== 阶段 1：Maven 构建 ==========
FROM ${MAVEN_IMAGE} AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DskipTests

COPY src ./src
RUN mvn -B -q package -DskipTests

# ========== 阶段 2：JRE 运行 ==========
FROM ${JRE_IMAGE}
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/resume-io-*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m"

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -s http://localhost:8080/ > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
