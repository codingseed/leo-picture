package com.leo.leopicturebackend.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * AI服务类
 * 处理与AI相关的业务逻辑
 */
public interface AiService {
    /**
     * 处理AI聊天流请求
     *
     * @param memoryId 内存ID（使用long类型以支持大数值）
     * @param userMessage 用户消息
     * @param request HTTP请求
     * @return ServerSentEvent流
     */
    Flux<ServerSentEvent<String>> chatStream(long memoryId, String userMessage, HttpServletRequest request);
}