package com.leo.leopicturebackend.manager;

import org.apache.curator.shaded.com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RateLimiterManager {
    // 每秒5个令牌,5秒预热

    /*    AI图像处理是计算密集型任务，需要大量CPU/GPU资源
        阿里云DashScope API有明确的调用频率限制
        成本考虑：API调用有费用，需要控制调用频率
        稳定性：避免对AI服务造成过大压力
        避免因频繁调用导致API被限流或账户被封禁*/
    private final RateLimiter rateLimiter = RateLimiter.create(5.0,5, TimeUnit.SECONDS);

    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return rateLimiter.tryAcquire();
    }
}
