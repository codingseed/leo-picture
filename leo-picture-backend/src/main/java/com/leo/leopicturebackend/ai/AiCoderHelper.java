package com.leo.leopicturebackend.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AiCoderHelper {

    @Resource   //通过类型注入
    private ChatModel qwenChatModel;

    private static final String SYSTEM_PROMPT = "你是编程领域的小助手，帮助用户解答编程学习和求职面试相关的问题，并给出建议。重点关注 4 个方向：\n" +
            "1. 规划清晰的编程学习路线\n" +
            "2. 提供项目学习建议\n" +
            "3. 给出程序员求职全流程指南（比如简历优化、投递技巧）\n" +
            "4. 分享高频面试题和面试技巧\n" +
            "请用简洁易懂的语言回答，助力用户高效学习与求职。\n";
    public String chat(String message) {
        SystemMessage systemMessage = SystemMessage.from(SYSTEM_PROMPT);
        UserMessage userMessage = UserMessage.from(message);

        ChatResponse chatResponse = qwenChatModel.chat(systemMessage,userMessage);

        AiMessage aiMessage = chatResponse.aiMessage();
        log.info("aiMessage:{}", aiMessage);

        return aiMessage.text();
    }
    public String chatWithUserMessage(UserMessage userMessage) {

        ChatResponse chat = qwenChatModel.chat(userMessage);

        AiMessage aiMessage = chat.aiMessage();
        return aiMessage.text();
    }


}
