package com.leo.leopicturebackend.controller;

import com.leo.leopicturebackend.ai.AiCoderHelperServices;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Resource
    private AiService aiService;

    /**
     * AI聊天流接口
     *
     * @param memoryId 内存ID
     * @param userMessage 用户消息
     * @param request HTTP请求
     * @return ServerSentEvent流
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam(required = false, defaultValue = "1") int memoryId,
            @RequestParam("message") String userMessage,
            HttpServletRequest request) {

        log.info("收到AI聊天请求, memoryId: {}, message: {}", memoryId, userMessage);

        return aiService.chatStream(memoryId, userMessage, request)
                .doOnSubscribe(subscription -> log.info("SSE连接建立"))
                .doOnComplete(() -> log.info("SSE流完成"))
                .doOnError(error -> log.error("SSE流错误", error));
    }
}