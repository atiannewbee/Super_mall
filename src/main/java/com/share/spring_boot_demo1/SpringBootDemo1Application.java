package com.share.spring_boot_demo1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SUPER MALL 后端应用入口。
 *
 * <p>启用配置属性扫描和定时任务；数据库结构由 Flyway 在应用启动阶段校验并迁移。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SpringBootDemo1Application {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootDemo1Application.class, args);
    }

}
