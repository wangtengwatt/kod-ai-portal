package com.kod.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>扫描 {@code com.kod.mapper} 下的 Mapper 接口，并注册分页插件。</p>
 */
@Slf4j
@Configuration
@MapperScan("com.kod.mapper")
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 核心拦截器，内置分页插件。
     *
     * <p>默认按 MySQL 方言分页；如切换数据库需调整 {@link DbType}。</p>
     *
     * @return 已装配分页插件的拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        log.info("初始化 MyBatis-Plus 拦截器（分页插件，DbType=MYSQL）");
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
