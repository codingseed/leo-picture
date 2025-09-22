package com.leo.leopicturebackend;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;


@SpringBootApplication
@MapperScan("com.leo.leopicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class LeoPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeoPictureBackendApplication.class, args);
    }

}
