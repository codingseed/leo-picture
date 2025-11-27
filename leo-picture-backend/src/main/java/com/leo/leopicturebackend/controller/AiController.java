package com.leo.leopicturebackend.controller;

import com.leo.leopicturebackend.service.AiService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;


@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Resource
    private AiService aiService;

    /**
     * AI聊天流接口
     *
     * @param memoryId 内存ID/会话ID（使用String类型以支持复杂格式的会话ID）
     * @param userMessage 用户消息
     * @param request HTTP请求
     * @return ServerSentEvent流
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam(name="chatId", required = false, defaultValue = "1") String memoryId,
            @RequestParam("message") String userMessage,
            HttpServletRequest request) {

        log.info("收到AI聊天请求, memoryId: {}, message: {}", memoryId, userMessage);

        // 记录用户IP地址
        String userIp = request.getRemoteAddr();
        log.info("用户IP地址: {}", userIp);

        // 调用AI服务进行聊天流处理
        return aiService.chatStream(memoryId, userMessage, request)
                .doOnSubscribe(subscription -> log.info("SSE连接建立"))
                .doOnComplete(() -> log.info("SSE流完成"));
    }
}