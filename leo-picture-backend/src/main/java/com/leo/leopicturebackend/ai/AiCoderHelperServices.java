package com.leo.leopicturebackend.ai;



import com.leo.leopicturebackend.ai.guardrail.SafeInputGuardrail;
import dev.langchain4j.service.*;
import dev.langchain4j.service.guardrail.InputGuardrails;
import reactor.core.publisher.Flux;

import java.util.List;


@InputGuardrails(SafeInputGuardrail.class)
public interface AiCoderHelperServices {
//    @SystemMessage(fromResource = "system-prompt.txt")
//    String chat(@MemoryId int memoryId, @UserMessage String UserMessage);
//
//    //SystemMessage系统预设，结合本地文档，设置初始提示语
//    @SystemMessage(fromResource = "system-prompt.txt")
//    Record chatForRecord(@UserMessage String UserMessage);
//
//    //学习报告
//    record Record(String name, List<String> suggestionlist){}
//
//    @SystemMessage(fromResource = "system-prompt.txt")
//    Result<String> ChatWithRag(String UserMessage);
//
//    //MemoryId用来会话隔离，不同的ID会话会话记录不同
//    @SystemMessage(fromResource = "system-prompt.txt")
//    Flux <String> chatStream(@MemoryId int memoryId, @UserMessage String UserMessage);

    @SystemMessage(fromResource = "system-prompt.txt")
    Flux<String> chatStream(@MemoryId long memoryId, @UserMessage String UserMessage, @V("userId") String userId, @V("userAccount") String userAccount);

//    /**
//     * 生成图片的方法
//     * @param prompt 图片描述
//     * @return 生成结果
//     */
//    @SystemMessage("你是一个AI助手，可以根据用户的需求生成图片。当用户需要生成图片时，请使用图像生成工具。")
//    Result<String> generateImage(@UserMessage String prompt);
}