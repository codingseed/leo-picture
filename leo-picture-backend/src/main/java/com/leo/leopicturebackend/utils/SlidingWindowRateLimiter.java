package com.leo.leopicturebackend.utils;

import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConfigurationProperties(prefix = "window.rate")
@Data
@Slf4j
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

    // Lua脚本，保证原子性操作
    private static final String LUA_SCRIPT =
            "local key = KEYS[1]\n" +
                    "local limit = tonumber(ARGV[1])\n" +
                    "local windowSize = tonumber(ARGV[2])\n" +
                    "local now = tonumber(ARGV[3])\n" +
                    "local requestId = ARGV[4]\n" +
                    "\n" +
                    "local windowStart = now - windowSize\n" +
                    "\n" +
                    "-- 移除过期数据\n" +
                    "redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)\n" +
                    "\n" +
                    "-- 获取当前数量\n" +
                    "local currentCount = redis.call('ZCARD', key)\n" +
                    "\n" +
                    "if currentCount >= limit then\n" +
                    "    return {0, currentCount, 0}  -- 拒绝\n" +
                    "else\n" +
                    "    -- 添加新请求\n" +
                    "    redis.call('ZADD', key, now, requestId)\n" +
                    "    -- 设置过期时间（如果key不存在或过期时间小于剩余窗口时间）\n" +
                    "    local ttl = redis.call('TTL', key)\n" +
                    "    if ttl == -1 or ttl < windowSize + 10 then\n" +
                    "        redis.call('EXPIRE', key, windowSize + 10)\n" +
                    "    end\n" +
                    "    return {1, currentCount + 1, limit - currentCount - 1}  -- 允许\n" +
                    "end";
    /**
     * 尝试获取令牌（原子操作版本）
     */
    public boolean tryAcquire(String key) {
        long now = Instant.now().getEpochSecond();
        String requestId = UUID.randomUUID().toString();//使用UUID作为value，避免相同时间戳的数据覆盖
        String redisKey = "rate_limit:" + key;

        try {
            DefaultRedisScript<List> script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);

            List<Long> result = redisTemplate.execute(
                    script,
                    Collections.singletonList(redisKey),
                    limit,
                    windowSize,
                    now,
                    requestId);

            if (result != null && result.size() >= 3) {
                return result.get(0) == 1;
            }
            return false;
        } catch (Exception e) {
            log.warn("Rate limiter Redis exception, allowing request for key: {}", key, e);
            // Redis异常时，根据业务需求决定是否放行
            //限流组件异常时允许请求，避免影响正常业务
            return true;
        }
    }
    /**
     * 获取当前窗口内的请求数量
     */
    public long getCurrentCount(String key) {
        String redisKey = "rate_limit:" + key;
        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSize;

        ZSetOperations<String, Object> zSet = redisTemplate.opsForZSet();
        // 先清理过期数据
        zSet.removeRangeByScore(redisKey, 0, windowStart);
        return zSet.zCard(redisKey);
    }


    /**
     * 获取剩余请求次数
     */
    public long getRemainingCount(String key) {
        long currentCount = getCurrentCount(key);
        return Math.max(0, limit - currentCount);
    }

    /**
     * 获取窗口重置时间（秒）
     */
    public long getResetTime(String key) {
        String redisKey = "rate_limit:" + key;
        ZSetOperations<String, Object> zSet = redisTemplate.opsForZSet();

        // 获取最早的有效请求
        Set<Object> oldestRequests = zSet.range(redisKey, 0, 0);
        if (oldestRequests == null || oldestRequests.isEmpty()) {
            return 0;
        }

        // 获取最早请求的时间戳
        Double oldestTimestamp = zSet.score(redisKey, oldestRequests.iterator().next());
        if (oldestTimestamp == null) {
            return 0;
        }

        long resetTime = (long) (oldestTimestamp + windowSize) - Instant.now().getEpochSecond();
        return Math.max(0, resetTime);
    }

//    /**
//     * 尝试获取令牌
//     *
//     * @return true表示允许请求，false表示拒绝请求
//     */
//    public boolean tryAcquire(String key){
//        long now = Instant.now().getEpochSecond();//获取当前时间戳
//        long windowStart = now -  windowSize * 1000L;//将秒转换为毫秒的乘数
//
//        String redisKey = "rate_limit:" + key;
//        ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
//        // 移除时间窗口之前的请求,避免缓存中的数据过多,即移除windowStart开始计算前的0分钟的数据
//        zSetOperations.removeRangeByScore(redisKey, 0, windowStart);
//        // 获取当前时间窗口内的请求数
//        Long currentCount = zSetOperations.zCard(redisKey);
//        if (currentCount >= limit){
//            return false;
//        }
//        // 添加记录当前请求到时间窗口内
//        zSetOperations.add(redisKey,String.valueOf(now),now);
//
//        // 设置过期时间
//        redisTemplate.expire(redisKey, windowSize, TimeUnit.SECONDS);
//
//        return true;
//    }
}
