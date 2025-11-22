package com.leo.leopicturebackend.ai.tools;

import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class AILimiterService {

    /*
    用户请求生图
        ↓
    检查每日限制（5次/天）
        ↓ ❌ 已用5次 → 返回"今日次数已用完"
        ↓ ✅ 还有次数
    检查频率限制（2次/分钟）
        ↓ ❌ 1分钟内已用2次 → 返回"操作太频繁"
        ↓ ✅ 通过检查
    更新两个计数器
        ↓
    执行生图操作
        ↓
    返回成功结果 + 剩余次数
    使用lua脚本目的就是保证原子性
    * */
    /**
     * 分层限流脚本：
     * 1. 每日总次数限制（5次/天）
     * 2. 短时频率限制（防止恶意刷接口）
     */
    private static final String DAILY_LIMIT_SCRIPT = """
        local dailyKey = KEYS[1]    -- 每日限制key
        local freqKey = KEYS[2]     -- 频率限制key
        local now = tonumber(ARGV[1])
        local dailyCapacity = tonumber(ARGV[2]) -- 每日最多取5次
        local freqCapacity = tonumber(ARGV[3])  -- 每分钟最多取2次
        local freqWindow = tonumber(ARGV[4])    -- 频率检查周期
        local requested = tonumber(ARGV[5]) -- 本次要取1次
        
        -- 检查参数是否有效
        if not now or not dailyCapacity or not freqCapacity or not freqWindow or not requested then
            return {0, 0, 0, '参数错误'}
        end
        
        -- 1. 检查每日限制
        local dailyCount = tonumber(redis.call('get', dailyKey)) or 0
        if dailyCount >= dailyCapacity then
            local ttl = redis.call('ttl', dailyKey)
            if ttl < 0 then
                ttl = 24 * 3600  -- 默认24小时
            end
            return {0, 0, ttl * 1000, 'daily'}  -- 返回：拒绝，剩余0次，等待时间，限制类型
        end
        
        -- 2. 检查频率限制
        local freqCount = tonumber(redis.call('get', freqKey)) or 0
        if freqCount >= freqCapacity then
            local ttl = redis.call('ttl', freqKey)
            if ttl < 0 then
                ttl = 60  -- 默认1分钟
            end
            return {0, dailyCapacity - dailyCount - 1, ttl * 1000, 'frequency'}  -- 频率限制
        end
        
        -- 3. 通过限制，更新计数
        -- 更新每日计数（设置24小时过期）
        redis.call('incrby', dailyKey, requested)   -- 今日次数+1
        if dailyCount == 0 then
            redis.call('expire', dailyKey, 24 * 3600)  -- 如果是今天第一次，设置24小时过期
        end
        
        -- 更新频率计数（设置1分钟过期）
        redis.call('incrby', freqKey, requested) -- 近期次数+1
        if freqCount == 0 then
            redis.call('expire', freqKey, freqWindow / 1000) -- 如果是周期内第一次，设置1分钟过期，转换为秒
        end
        -- 返回成功
        return {1, dailyCapacity - dailyCount - requested, 0, 'success'}
        """;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 限流方法：每日5次 + 每分钟2次频率限制
     */
    public RateLimitResult tryAcquire(String userId) {
        // 检查用户ID是否有效
        if (userId == null || userId.isEmpty()) {
            log.warn("用户ID为空，放行请求");
            return new RateLimitResult(true, 5, 0);
        }
        
        String dailyKey = "ai:image_gen:daily:" + userId;
        String freqKey = "ai:image_gen:freq:" + userId;

        long now = System.currentTimeMillis();
        // 限流参数配置
        int dailyCapacity = 5;           // 每日总次数
        int freqCapacity = 2;            // 每分钟频率限制
        long freqWindow = 60 * 1000;     // 频率窗口：1分钟
        int requested = 1;               // 每次请求消耗1次

        List<String> keys = Arrays.asList(dailyKey, freqKey);
        List<String> args = Arrays.asList(
                String.valueOf(now),
                String.valueOf(dailyCapacity),
                String.valueOf(freqCapacity),
                String.valueOf(freqWindow),
                String.valueOf(requested)
        );

        try {
            // 创建一个专门用于脚本执行的RedisTemplate，确保正确的序列化器
            DefaultRedisScript<List> script = new DefaultRedisScript<>(DAILY_LIMIT_SCRIPT, List.class);
            
            // 使用StringRedisTemplate执行脚本，避免序列化问题
            List result = stringRedisTemplate.execute(
                    script,
                    keys,
                    args.toArray(new String[0])
            );

            if (result != null && result.size() >= 4) {
                // 正确处理返回值类型，Lua脚本返回的数字类型可能是Long
                boolean allowed = Long.valueOf(1).equals(result.get(0));
                long remaining = ((Number) result.get(1)).longValue();
                long waitTime = ((Number) result.get(2)).longValue();
                // 将 limitType 改为字符串处理
                String limitType = String.valueOf(result.get(3)); // 限制类型（方便调试）

                log.debug("限流检查 - 用户: {}, 允许: {}, 剩余: {}, 等待: {}ms, 类型: {}",
                        userId, allowed, remaining, waitTime, limitType);

                return new RateLimitResult(allowed, remaining, waitTime);
            } else {
                log.warn("限流脚本返回结果异常: {}", result);
                return new RateLimitResult(true, dailyCapacity - 1, 0);
            }

        } catch (Exception e) {
            log.error("限流脚本执行失败, 放行请求以保障可用性。用户: {}", userId, e);
            // 故障时放行，但记录日志用于后续分析
            return new RateLimitResult(true, dailyCapacity - 1, 0);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?") // 每天午夜执行删除无用key
    public void resetDailyLimits() {
        // todo使用Redis的SCAN命令查找所有匹配的key并删除或者使用Redis的KEYS命令（在生产环境中要注意性能影响）
    }

    /**
     * 限流结果封装
     */
    @Data
    @AllArgsConstructor
    public static class RateLimitResult {
        private boolean allowed;       // 是否允许通过
        private long remainingTokens;  // 剩余令牌数
        private long waitTimeMs;       // 需要等待的时间（毫秒）
    }

}