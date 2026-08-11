package com.kod;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * kod 官网后端服务启动类。
 *
 * <p>基于 Spring Boot 3 + MyBatis-Plus，提供官网所需的基础接口。</p>
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class KodPortalApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 自动加载项目根目录 .env 文件（本地开发），docker-compose 部署时已注入，加载失败不影响启动
        Dotenv dotenv = Dotenv.configure()
                .directory("../")
                .ignoreIfMissing()
                .load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));

        Environment env = SpringApplication.run(KodPortalApplication.class, args).getEnvironment();
        // 启动完成后输出关键信息，便于确认运行端口与激活的环境
        String port = env.getProperty("server.port", "8080");
        String profiles = String.join(",", env.getActiveProfiles());
        log.info("kod-portal-backend 启动成功！激活环境=[{}], 健康检查=http://localhost:{}/api/health",
                profiles.isEmpty() ? "default" : profiles, port);
    }
}
