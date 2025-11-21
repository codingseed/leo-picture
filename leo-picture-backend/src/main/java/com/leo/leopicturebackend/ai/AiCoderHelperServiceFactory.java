package com.leo.leopicturebackend.ai;

import com.leo.leopicturebackend.ai.tools.ImageGenerationTool;
import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCoderHelperServiceFactory {

    @Resource
    private ChatModel myQwenChatModel;
    @Resource
    private ContentRetriever contentRetriever;

    @Resource
    private McpToolProvider mcpToolProvider;
    @Resource
    private StreamingChatModel qwenStreamChatModel;
    
    @Resource
    private ImageGenerationTool imageGenerationTool;

    @Resource
    private ZhipuAiChatModel myZhipuChatModel;

    @Bean
    public AiCoderHelperServices aiCoderHelperServices() {
        //会话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);//最多保存10条会话记录记忆,默认存内存里面
        //构造AiServices
        return AiServices.builder(AiCoderHelperServices.class)
                .chatModel(myQwenChatModel)
                .chatMemory(chatMemory)
                .streamingChatModel(qwenStreamChatModel)//使用千问的流式输出模型
                .contentRetriever(contentRetriever)//rag检索增强生成
                .chatMemoryProvider( memoryId -> MessageWindowChatMemory.withMaxMessages(10))//每个会话独立存储
                .toolProvider(mcpToolProvider)//mcp工具,使用他人服务器上部署的rag模型
                .tools(imageGenerationTool)//图像生成工具
                .build();
    }
}