package com.leo.leopicturebackend.ai.model;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.community.dashscope.chat-model")
@Data
public class QwenChatModelConfig {

    private String modelName;

    private String apiKey;

    @Resource
    private ChatModelListener chatModelListener;

    @Bean
    @Primary
    public QwenChatModel myQwenChatModel() {
        // 从环境变量读取 API Key

        QwenChatModel qwenChatModel = QwenChatModel.builder()
                .modelName(modelName)
                .apiKey(apiKey)
                .listeners(List.of(chatModelListener))//监听请求、响应和错误的监听器。
                .topP(0.7D) //官方范围(0-1]，默认0.7。top_p用于控制生成文本的随机性，top_p 越高，生成的文本越多样化,官方建议不要和temperature同时修改
//                .temperature(0.7F)//模型温度，官方范围[0-2)，默认0.7。温度越高，生成的文本越多样化
                .enableSearch(false)//是否启用搜索
                .build();
        return qwenChatModel;
    }
}
