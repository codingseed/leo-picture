package com.leo.leopicturebackend.ai.model;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Data
public class ZhipuChatModerConfig {

    private String modelName = "glm-4-flash";

    @Value("${bigmodel.api-key}")
    private String apiKey;

    @Resource
    private ChatModelListener chatModelListener;

    @Bean
    public ZhipuAiChatModel myZhipuChatModel() {
        ZhipuAiChatModel ZhipuChatModel = ZhipuAiChatModel.builder()
                .model(modelName)
                .apiKey(apiKey)
                .listeners(List.of(chatModelListener))
                .build();

        return ZhipuChatModel;
    }

}
