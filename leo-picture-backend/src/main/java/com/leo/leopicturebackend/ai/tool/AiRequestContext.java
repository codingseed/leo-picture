package com.leo.leopicturebackend.ai.tool;



import com.alibaba.ttl.TransmittableThreadLocal;
import com.leo.leopicturebackend.model.entity.User;
import org.springframework.stereotype.Component;

/**
 * AI工具中使用的用户信息DTO
 * 用于在AI工具调用时传递用户信息
 * 线程传递支持：TTL 是增强版的 ThreadLocal，支持线程池间的值传递
 * 通过 doOnSubscribe 和 doOnTerminate 确保正确的生命周期管理
 * Langchain4j兼容：Langchain4j使用线程池执行工具，TTL能正确传递
 * 算是一个TTL的尝试，但目前已经没有使用，AI自行解决
 */

@Component
@Deprecated
public class AiRequestContext {

    private static final TransmittableThreadLocal<Long> CURRENT_USER_ID = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<User> CURRENT_USER = new TransmittableThreadLocal<>();

    public static void setCurrentUser(User user) {
        CURRENT_USER_ID.set(user.getId());
        CURRENT_USER.set(user);
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static User getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
        CURRENT_USER.remove();
    }
}