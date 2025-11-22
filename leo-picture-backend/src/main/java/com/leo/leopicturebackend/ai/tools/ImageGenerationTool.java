package com.leo.leopicturebackend.ai.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.gson.Gson;
import com.leo.leopicturebackend.ai.tool.AiRequestContext;
import com.leo.leopicturebackend.common.ResultUtils;
import com.leo.leopicturebackend.exception.ErrorCode;
import com.leo.leopicturebackend.model.entity.User;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Langchain4j内部使用ExecutorService线程池
 * 实际工作流程
 * 工具调用的本质 并不是 AI 服务器自己调用这些工具、也不是把工具的代码发送给 AI 服务器让它执行，
 * 它只能提出要求，表示 “我需要执行 XX 工具完成任务”。
 * 而真正执行工具的是我们自己的应用程序，执行后再把结果告诉 AI，让它继续工作。
 */
@Slf4j
@Component
public class ImageGenerationTool {

    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    @Resource
    private AILimiterService limiterService;
    // 创建TTL安全的线程池
    // 使用静态初始化确保TTL线程池正确创建
    // 使用正确的API端点URL
    String IMAGE_GENERATION_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

//    @Resource
//    private Gson gson;

    /**
     * 根据文本描述生成图片
     *
     * @param prompt 图像描述文本
     * @return 生成的图片信息
     */
    @Tool(name = "imageGeneration", value = """
当用户请求生成图片、绘制图像或需要视觉内容时调用此工具。
特别适用于：山水画、风景图、人物画像、产品设计图等视觉内容生成。
输入应该是详细的中文描述，包含场景、风格、颜色等细节。
返回一个任务ID用于获取生成结果。
""")
    public String generateImages(@P("详细的中文图像描述，如'一幅水墨风格的山水画，有远山、流水和小桥'") String prompt,
                                 @P("用户ID，用于频率限制和身份验证") String userId,@P("用户账号，用于日志记录") String userAccount) {
        log.info("AI调用图片生成工具，接收到的参数 - prompt: {}", prompt);

        // 使用TTL安全的异步执行
        try {
            // ← 线程池线程读取
//            // 使用TransmittableThreadLocal获取用户信息
//            final User currentUser = AiRequestContext.getCurrentUser();
//            if (currentUser == null) {
//                log.error("未获取到用户上下文");
//                return "系统错误：请重新登录";
//            }

            // 直接传递用户ID，不依赖TTL
            log.info("📝 直接处理 - 用户ID: {}, 账号: {}", userId, userAccount);
            // 频率限制检查
            // 直接在主线程进行频率检查
            if (!limiterService.tryAcquire(String.valueOf(userId)).isAllowed()) {
                return "您今天生成图片的次数已达上限（每天5次，每分钟不超过2次），请明天再试。";
            }
            return generateImageWithUser(prompt);
        } catch (Exception e) {
            log.error("工具执行异常", e);
            return "图片生成服务暂时不可用，请稍后重试";
        }
    }

    /**
     * 实际生成图片的方法
     * @param prompt 图像描述
     * @return 生成结果
     */
    private String generateImageWithUser(String prompt) {
        try {
            log.info("Generating image with prompt: {}", prompt);

            // 构建符合API要求的请求体
            // model备选：首选qwen-image-plus。wan2.2-t2i-plus、wan2.2-t2i-flash、wan2.2-t2i-flash。低成本：wanx2.0-t2i-turbo
            String requestBody = String.format("""
        {
            "model": "qwen-image-plus",
            "input": {
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            {
                                "text": "%s"
                            }
                        ]
                    }
                ]
            },
            "parameters": {
                "n": 1,
                "size": "1328*1328",
                "prompt_extend": true,
                "watermark": true
            }
        }
        """, prompt.replace("\"", "\\\""));

            log.debug("Request body: {}", requestBody);

            // 创建 HTTP 客户端
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            // 创建请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(IMAGE_GENERATION_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            // 发送请求
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 检查响应状态码
            int statusCode = response.statusCode();
            String responseBody = response.body();
            log.info("Image generation response status: {}, body: {}", statusCode, responseBody);

            if (statusCode >= 200 && statusCode < 300) {
//                JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
//                if (responseJson.has("output") && responseJson.getAsJsonObject("output").has("task_id")) {
//                    String taskId = responseJson.getAsJsonObject("output").get("task_id").getAsString();
//
//                    // 返回特殊格式，让前端识别为图片生成任务
//                    return "TASK_ID:" + taskId;
//                }

                // 如果没有task_id，返回原始响应
                return responseBody;
            } else {
                log.error("Image generation failed with status code: {}, response: {}",
                        statusCode, responseBody);

                return "图片生成失败: " + ResultUtils.error(ErrorCode.OPERATION_ERROR,responseBody);
            }
        } catch (Exception e) {
            log.error("Failed to generate image", e);

            return "图片生成异常: " + e.getMessage();
        }
    }
}