package com.leo.leopicturebackend.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConfigurationProperties(prefix = "redisson")
@Data
@Slf4j
public class RedissonConfig {
    private Lock lock = new Lock();
    private Client client = new Client();
    private String password;

    @Data
    public static class Lock {
        private Long defaultLockTtl = 30000L; // 锁默认过期时间（30秒）
        private Long retryInterval = 1000L;   // 获取锁失败时重试间隔（1秒）
        private Integer retryAttempts = 3;    // 获取锁最大重试次数（3次）
    }

    @Data
    public static class Client {
        private Integer connectionPoolSize = 64;     // 连接池大小
        private Long idleConnectionTimeout = 300000L; // 连接空闲超时时间（5分钟）
    }
    
    //配置Redisson工厂
    @Bean
    public RedissonClient redissonClient() {
        try {
            //创建配置
            Config config = new Config();

            config.useSingleServer()
                    .setAddress("redis://127.0.0.1:6379")
                    .setDatabase(0)
                    .setConnectionPoolSize(client.getConnectionPoolSize())
                    .setIdleConnectionTimeout(client.getIdleConnectionTimeout().intValue())
                    .setRetryAttempts(lock.getRetryAttempts())
                    .setRetryInterval(lock.getRetryInterval().intValue());
            
            // 只有当密码不为空时才设置密码
            if (StringUtils.hasText(password)) {
                config.useSingleServer().setPassword(password);
            }
            
            RedissonClient redisson = Redisson.create(config);
            log.info("Redisson客户端初始化成功");
            return redisson;
        } catch (Exception e) {
            log.error("Redisson客户端初始化失败：", e);
            throw e;
        }
    }
}