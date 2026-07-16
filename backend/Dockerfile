# ---------- 构建阶段：Maven 打包 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先只拷贝 pom，利用缓存预下载依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
# 再拷贝源码构建
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段：JRE 运行 jar ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
# 时区设为上海，容器内 JVM 使用
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
