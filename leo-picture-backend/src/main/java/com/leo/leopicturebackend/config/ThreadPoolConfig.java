package com.leo.leopicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/*基于配置文件的动态调整动态调整线程池*/
@Component
@Data
@ConfigurationProperties(prefix = "thread.pool")
public class ThreadPoolConfig {

    private int corePoolSize = 10;        // 默认值
    private int maximumPoolSize = 20;     // 默认值
    private int keepAliveTime = 60;       // 默认值
    private int queueCapacity = 100;      // 默认值

    @Bean("pictureUploadExecutor")
    public ThreadPoolExecutor pictureUploadExecutor(){

        return new ThreadPoolExecutor(
                 corePoolSize,
            maximumPoolSize,
             keepAliveTime,
                TimeUnit.SECONDS, new LinkedBlockingQueue<>(queueCapacity), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r,"upload-thread-");
                thread.setDaemon(false);
                return thread;
            }
        }, new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
