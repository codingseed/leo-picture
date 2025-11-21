package com.leo.leopicturebackend.controller;

import com.leo.leopicturebackend.ai.AiCoderHelperServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
    private AiCoderHelperServices aiCoderHelperServices;

    @GetMapping("/chat")
    public Flux<ServerSentEvent<String>> chatStream(int memoryId, @RequestParam("message") String UserMessage){
        return aiCoderHelperServices.chatStream(memoryId, UserMessage)
                .map(chunk -> {
                    // 检查是否为图片URL的Markdown格式
                    String processedChunk = processImageUrl(chunk);
                    return ServerSentEvent.<String>builder()
                            .data(processedChunk)
                            .build();
                });
    }

    private String processImageUrl(String content) {
        // 更宽松的图片URL匹配，支持带查询参数的URL
        Pattern pattern = Pattern.compile("(https?://[^\\s]+\\.(png|jpg|jpeg|gif|webp)(?:\\?[^\\s]*)?)");
        Matcher matcher = pattern.matcher(content);

        // 或者更宽松的版本，匹配任何包含图片扩展名的URL
        Pattern pattern2 = Pattern.compile("(https?://[^\\s]*\\.(png|jpg|jpeg|gif|webp)[^\\s]*)");
        Matcher matcher2 = pattern2.matcher(content);

        if (matcher.find()) {
            String imageUrl = matcher.group(1);
            return "[IMAGE_URL]" + imageUrl;
        } else if (matcher2.find()) {
            String imageUrl = matcher2.group(1);
            return "[IMAGE_URL]" + imageUrl;
        }
        return content;
    }


}
