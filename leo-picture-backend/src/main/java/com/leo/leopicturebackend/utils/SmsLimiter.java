package com.leo.leopicturebackend.utils;

import com.leo.leopicturebackend.exception.SmsException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SmsLimiter {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    private final String SMS_PREFIX = "sms:";
    private final String CODE_PREFIX = "code:";
    private final long CODE_EXPIRE_TIME = 300; // 5分钟

    @Resource
    private SlidingWindowRateLimiter rateLimiter;
    // 添加幂等性相关常量
    private final long IDEMPOTENT_EXPIRE_TIME = 60; // 1分钟内不允许重复发送


    /**
     *使用滑动窗口算法限流
     * @param phone
     * @param code
     * @return
     */
    public boolean sendSmsAuth(String phone, String code) throws SmsException {
        // 幂等性key设置
        String idempotentKey = String.format("idempotent:%s",phone) ;

        // 滑动窗口限流检查
        if (!rateLimiter.tryAcquire(SMS_PREFIX+phone)){
            throw new SmsException("短信发送过于频繁，请稍后再试");
        }
        // 设置幂等性标识，1表示已发送,放redis里一分钟
        redisTemplate.opsForValue().set(idempotentKey, "1", IDEMPOTENT_EXPIRE_TIME, TimeUnit.SECONDS);
        // 保存验证码
        String key = CODE_PREFIX + phone;
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_TIME, TimeUnit.SECONDS);
        return true;

    }


    /**
     * 验证手机号对应的验证码是否正确
     * @param phoneNumber
     * @param code
     * @return
     */
    public boolean verifyCode(String phoneNumber, String code){
        String key = CODE_PREFIX + phoneNumber;
        String redisCode = redisTemplate.opsForValue().get(key);
        if (redisCode == null||!redisCode.equals(code)){
            return false;
        }else{
            redisTemplate.delete(key);
        }
        return true;
    }

}
