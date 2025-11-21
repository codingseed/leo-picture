package com.leo.leopicturebackend.utils;

import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConfigurationProperties(prefix = "window.rate")
@Data
public class SlidingWindowRateLimiter {

    /**
     * @param key Redis键，用于标识不同的限流对象（如手机号、IP等）
     * @param limit 时间窗口内的最大请求数
     * @param windowSize 时间窗口大小（秒）,多少秒内允许请求的次数
     */
    private int limit;
    private int windowSize;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;


    /**
     * 尝试获取令牌
     *
     * @return true表示允许请求，false表示拒绝请求
     */
    public boolean tryAcquire(String key){
        long now = Instant.now().getEpochSecond();//获取当前时间戳
        long windowStart = now -  windowSize * 1000L;//将秒转换为毫秒的乘数

        String redisKey = "rate_limit:" + key;
        ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
        // 移除时间窗口之前的请求,避免缓存中的数据过多,即移除windowStart开始计算前的0分钟的数据
        zSetOperations.removeRangeByScore(redisKey, 0, windowStart);
        // 获取当前时间窗口内的请求数
        Long currentCount = zSetOperations.zCard(redisKey);
        if (currentCount >= limit){
            return false;
        }
        //添加记录当前请求到时间窗口内
        zSetOperations.add(redisKey,String.valueOf(now),now);

        //设置过期时间
        redisTemplate.expire(redisKey, windowSize, TimeUnit.SECONDS);

        return true;
    }
}
