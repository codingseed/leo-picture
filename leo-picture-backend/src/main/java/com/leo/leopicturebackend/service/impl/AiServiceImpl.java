package com.leo.leopicturebackend.service.impl;

import com.leo.leopicturebackend.ai.AiCoderHelperServices;
import com.leo.leopicturebackend.ai.tool.AiRequestContext;
import com.leo.leopicturebackend.model.entity.User;
import com.leo.leopicturebackend.service.AiService;
import com.leo.leopicturebackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI服务实现类
 * 处理与AI相关的业务逻辑
 */
@Service
@Slf4j
public class AiServiceImpl implements AiService {

    @Autowired
    private UserService userService;

    @Autowired
    private AiCoderHelperServices aiCoderHelperServices;

// doOnSubscribe：在流订阅时立即执行，在主线程设置 TTL
// TTL 包装：由于订阅发生在主线程，TTL 能正确捕获上下文
// 线程切换安全：即使 LangChain4J 切换到其他线程，TTL 也能传递上下文
// 自动清理：doOnTerminate 确保上下文不会泄漏
// Reactor Context 是响应式流内部的上下文存储
// 在流的整个生命周期中自动传递，不受线程切换影响
// 这是最可靠的传递机制
    @Override
    public Flux<ServerSentEvent<String>> chatStream(int memoryId, String userMessage, HttpServletRequest request) {
        // 1. 获取当前登录用户
        // 2. 使用 Reactor Context 传递用户信息
        return Flux.defer(() -> {   //Flux.defer() - 延迟执行 确保用户认证逻辑在流被订阅时才执行，而不是在方法调用时
            try {
                User loginUser = userService.getLoginUser(request);
                if (loginUser == null) {
                    return Flux.just(ServerSentEvent.<String>builder()
                            .data("错误：用户未登录")
                            .build());
                }

                log.info("用户已登录，用户ID: {}, 用户账号: {}", loginUser.getId(), loginUser.getUserAccount());

                // 使用 Reactor Context 传递用户信息
                return aiCoderHelperServices.chatStream(memoryId, userMessage)
                        // 设置 Reactor Context：在响应式流的整个处理链中自动传递
                        .contextWrite(reactor.util.context.Context.of("currentUser", loginUser))
                        .map(chunk -> {
                            // 可以从 context 中获取用户信息
                            User contextUser = AiRequestContext.getCurrentUser();
                            if (contextUser == null) {
//                                优雅降级：即使一层失效，另一层仍能工作
                                log.warn("TTL上下文丢失，但Reactor Context正常工作");
                            }
                            return ServerSentEvent.<String>builder()
                                    .data(processImageUrl(chunk))
                                    .build();
                        })
                        .doOnSubscribe(subscription -> {
                            // 设置TTL上下文
                            AiRequestContext.setCurrentUser(loginUser);
                        })
                        .doOnTerminate(() -> {
                            // 清理TTL上下文
                            AiRequestContext.clear();
                        })
                        .doOnError(error -> {
                            log.error("AI流处理错误", error);
                            AiRequestContext.clear();
                        });

            } catch (Exception e) {
                log.error("AI聊天流处理异常", e);
                return Flux.just(ServerSentEvent.<String>builder()
                        .data("服务器内部错误：" + e.getMessage())
                        .build());
            }
        });
    }
    /*线程安全：Reactor Context 天生支持跨线程传递
双重保险：TTL + Reactor Context 确保万无一失
资源管理：自动清理，防止内存泄漏
优雅降级：即使一层失效，另一层仍能工作

Reactor 核心概念总结
概念	作用	在你的代码中的体现
Flux	        异步流	    SSE 流式响应
defer()	        延迟执行	    按需执行用户认证
contextWrite()	设置上下文	传递用户信息
doOnSubscribe	订阅回调	    设置 TTL
doOnTerminate	结束回调	    清理 TTL
    * */
    // 处理图片 URL 的方法
    private String processImageUrl(String text) {
        // 匹配图片URL的Markdown格式 ![image](url)
        String regex = "!\\[.*?\\]\\((.*?)\\)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String imageUrl = matcher.group(1);
            // 如果是图片URL，返回特殊格式供前端处理
            return "[IMAGE_URL]" + imageUrl + "[/IMAGE_URL]";
        }
        return text;
    }
}