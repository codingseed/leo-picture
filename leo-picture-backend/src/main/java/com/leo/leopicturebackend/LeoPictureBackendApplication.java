package com.leo.leopicturebackend;

import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithm;
import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication//(exclude = {ShardingSphereAutoConfiguration.class})
@MapperScan("com.leo.leopicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)//开启AOP功能,exposeProxy = true 参数的作用是将当前的代理对象暴露到 ThreadLocal 中
@EnableAsync//开启Spring的异步方法支持
public class LeoPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeoPictureBackendApplication.class, args);
    }

}
