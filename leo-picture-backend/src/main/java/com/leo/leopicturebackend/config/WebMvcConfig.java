package com.leo.leopicturebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置自定义异步任务执行器（线程池）
     */
    @Bean(name = "asyncTaskExecutor")
    public AsyncTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数（默认线程数）
        executor.setCorePoolSize(2);
        // 最大线程数（峰值线程数）
        executor.setMaxPoolSize(4);
        // 队列容量（超过核心线程数的请求放入队列缓冲）
        executor.setQueueCapacity(100);
        // 空闲线程存活时间（超过核心线程数的线程，空闲多久后销毁）
        executor.setKeepAliveSeconds(60);
        // 线程名称前缀（日志中便于识别）
        executor.setThreadNamePrefix("async-sse-");
        // 拒绝策略（队列满+最大线程数达到时，如何处理新请求）
        // CallerRunsPolicy：由发起请求的线程自己执行（避免直接拒绝）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化线程池
        executor.initialize();
        return executor;
    }

    /**
     * 注册自定义执行器到 Spring MVC
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 绑定自定义线程池
        configurer.setTaskExecutor(asyncTaskExecutor());
        // 异步请求超时时间（可选，默认无超时）
        configurer.setDefaultTimeout(30000); // 30秒超时
    }
}