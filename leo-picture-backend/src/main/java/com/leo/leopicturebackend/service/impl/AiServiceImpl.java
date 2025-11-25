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

    @Override
    public Flux<ServerSentEvent<String>> chatStream(long memoryId, String userMessage, HttpServletRequest request) {
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

                // ✅ 直接传递用户信息，无需TTL
                return aiCoderHelperServices.chatStream(memoryId, userMessage,
                                String.valueOf(loginUser.getId()), loginUser.getUserAccount())
                        .timeout(Duration.ofSeconds(30))
                        .map(chunk -> ServerSentEvent.<String>builder()
                                .data(processImageUrl(chunk))
                                .build())
                        .retry(2)
                        .doOnError(error -> log.error("AI流处理错误", error));

            } catch (Exception e) {
                log.error("AI聊天流处理异常", e);
                return Flux.just(ServerSentEvent.<String>builder()
                        .data("服务器内部错误：" + e.getMessage())
                        .build());
            }
        });
    }

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