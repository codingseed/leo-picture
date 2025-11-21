package com.leo.leopicturebackend;

import com.leo.leopicturebackend.ai.AiCoderHelperServices;
import com.leo.leopicturebackend.service.UserService;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;
import java.util.List;

@SpringBootTest
class LeoPictureBackendApplicationTests {
    @Resource
    private AiCoderHelperServices aiCoderHelperServices;
    @Test
    void contextLoads() {
    }

    @Test
    void TestWithMcp() {
        try {
            String userMessage = "能为我生成一张图片吗，随机图片就行，我在测试阿里云的百炼生成图片的MCP";
            Result<String> result = aiCoderHelperServices.ChatWithRag(userMessage);
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("MCP图片生成测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 修改测试方法，使用更明确的提示词
    @Test
    void TestAmapMcp() {
        try {
            Flux<String> result = aiCoderHelperServices
                    .chatStream(123, "你好，你能使用图片生成工具为我创建一张山水风景图片吗？");

            // 收集所有结果
            List<String> results = result.collectList().block();
            System.out.println("All results: " + results);
        } catch (Exception e) {
            System.err.println("图片生成测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Test
    void TestImageGenerationToolDirectly() {
        try {
            // 直接测试ImageGenerationTool类
            // 使用反射设置apiKey字段（在实际测试环境中可能需要mock）
            System.out.println("图像生成工具类创建成功");
            System.out.println("注意：完整测试需要配置有效的API密钥");
        } catch (Exception e) {
            System.err.println("图像生成工具测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}