package com.leo.leopicturebackend;

import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithm;
import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;


@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class})
@EnableAsync
@MapperScan("com.leo.leopicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class LeoPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeoPictureBackendApplication.class, args);
    }

}
