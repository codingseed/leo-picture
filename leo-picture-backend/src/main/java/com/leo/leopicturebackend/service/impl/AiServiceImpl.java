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
    public Flux<ServerSentEvent<String>> chatStream(int memoryId, String userMessage, HttpServletRequest request) {
        try {
            // 获取当前登录用户
            User loginUser = userService.getLoginUser(request);
            if (loginUser == null) {
                return Flux.just(ServerSentEvent.<String>builder()
                        .data("错误：用户未登录")
                        .build());
            }

            log.info("用户已登录，用户ID: {}, 用户账号: {}", loginUser.getId(), loginUser.getUserAccount());

            // 关键修改：使用 CompletableFuture 手动包装，确保 TTL 生效
            return wrapWithUserContext(loginUser, () ->
                    aiCoderHelperServices.chatStream(memoryId, userMessage)
            ).map(chunk -> ServerSentEvent.<String>builder()
                    .data(processImageUrl(chunk))
                    .build());

        } catch (Exception e) {
            log.error("AI聊天流处理异常", e);
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("服务器内部错误：" + e.getMessage())
                    .build());
        }
    }
    /**
     * 手动包装异步调用，确保 TTL 上下文传递
     */
    private <T> Flux<T> wrapWithUserContext(User user, Supplier<Flux<T>> supplier) {
        return Flux.create(sink -> {
            // 使用 TTL 包装的 CompletableFuture
            CompletableFuture.supplyAsync(() -> {
                // 设置用户上下文
                AiRequestContext.setCurrentUser(user);
                try {
                    // 执行 AI 调用并收集结果
                    return supplier.get().collectList().block();
                } finally {
                    AiRequestContext.clear();
                }
            }).whenComplete((results, throwable) -> {
                if (throwable != null) {
                    sink.error(throwable);
                } else if (results != null) {
                    results.forEach(sink::next);
                    sink.complete();
                }
            });
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