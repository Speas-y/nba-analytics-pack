package com.nbaanalytics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用 {@link AppProperties} 绑定（application.yml / 环境变量中以 app.* 开头） */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {}
