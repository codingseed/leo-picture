package com.leo.leopicturebackend.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LocalCacheManager {
    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10_000L) // 最大 10000 条
            // 缓存 5 分钟后移除
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public Cache<String, String> getLocalCache() {
        return LOCAL_CACHE;
    }

    /**
     * 清除匹配的本地缓存
     */
    public void clearCacheByPattern(String pattern) {
        // Caffeine 不直接支持 pattern 删除，可以通过维护 key 集合的方式实现
        // 或者直接清除所有缓存
        LOCAL_CACHE.invalidateAll();
    }
}
