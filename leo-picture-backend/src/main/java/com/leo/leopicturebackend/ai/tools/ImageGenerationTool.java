package com.leo.leopicturebackend.ai.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.google.gson.Gson;
import com.leo.leopicturebackend.ai.tool.AiRequestContext;
import com.leo.leopicturebackend.common.ResultUtils;
import com.leo.leopicturebackend.exception.ErrorCode;
import com.leo.leopicturebackend.exception.ThrowUtils;
import com.leo.leopicturebackend.model.entity.User;
import com.leo.leopicturebackend.model.enums.UserRoleEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
     *
     * 核心方法：调用Qwen-Image模型生成图像
     * @throws ApiException SDK调用异常（如API接口错误、参数非法等）
     * @throws NoApiKeyException API密钥缺失异常（未配置或配置错误）
     * @throws UploadFileException 文件上传异常（当前示例未涉及文件上传，预留异常）
     * @throws IOException JSON序列化/反序列化异常（如结果转换失败）
     */
    private String generateImageWithUser(String prompt)
            throws ApiException, NoApiKeyException, UploadFileException, IOException {

        System.out.println("提示词: " + prompt);

        // 1. 创建多模态对话客户端实例（阿里云SDK提供的工具类，用于发起生图请求）
        MultiModalConversation conv = new MultiModalConversation();

        // 2. 构建用户消息（生图的核心输入：角色+提示词）
        MultiModalMessage userMessage = MultiModalMessage.builder() // 使用建造者模式（Builder）创建消息对象，代码更简洁
                .role(UserRoleEnum.USER.getValue()) // 设置消息角色为"user"（API要求：生图请求必须由用户角色发起）
                .content( // 设置消息内容：List格式，API要求仅含1个text类型的元素
                        Arrays.asList( // 转为List（因content参数要求为集合类型）
                                // 单个元素为Map：key固定为"prompt"，value为正向提示词（描述生成图像的细节）
                                Collections.singletonMap(
                                        "text",prompt

                                        //因为方便，这里我直接用的String text来传参，如果用createImageTaskRequest可以参考下面                                  //createImageTaskRequest.getInput().getMessages().get(0).getContent().get(0).getText()
                                        //例如："一副典雅庄重的对联悬挂于厅堂之中，房间是个安静古典的中式布置，桌子上放着一些青花瓷，
                                        // 对联上左书“义本生知人机同道善思新”，右书“通云赋智乾坤启数高志远”， 横批“智启通义”，
                                        // 字体飘逸，中间挂在一着一副中国风的画作，内容是岳阳楼。"

                                )
                        )
                ).build(); // 完成消息对象构建

        // 3. 构建生图参数（可选参数，控制图像生成规则）
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("watermark", true); // 开启水印：生成的图像右下角添加“Qwen-Image生成”水印
        parameters.put("prompt_extend", true); // 开启提示词智能改写：SDK会优化输入的prompt，提升生图效果（耗时增加3-4秒）
        parameters.put("negative_prompt", ""); // 反向提示词：空字符串表示不限制“不希望出现的内容”
        parameters.put("size", "1328*1328"); // 图像分辨率：默认值，宽1328像素、高1328像素（1:1比例）

        // 4. 构建完整的生图请求参数（整合API密钥、模型名、消息、参数）
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey) // 传入阿里云API密钥（身份认证核心，缺失会抛NoApiKeyException）
                .model("qwen-image") // 指定调用的模型：固定为"qwen-image"（通义千问生图模型）
                .messages(Collections.singletonList(userMessage)) // 传入用户消息列表（API要求仅支持单轮对话，故用 singletonList 生成单元素列表）
                .parameters(parameters) // 传入步骤3构建的生图参数
                .build(); // 完成请求参数构建

        // 5. 发起生图请求并获取结果
        MultiModalConversationResult result = conv.call(param); // 调用SDK的call方法，发送请求到阿里云API
        ThrowUtils.throwIf(result.getOutput().getChoices().isEmpty(),ErrorCode.PARAMS_ERROR, "Choices为空");
        ThrowUtils.throwIf(result.getOutput().getChoices().get(0).getMessage().getContent().isEmpty(),ErrorCode.PARAMS_ERROR, "Content为空");

        Map<String, Object> imageUrlMap = result.getOutput().getChoices().get(0).getMessage().getContent().get(0);
        return (String)imageUrlMap.get("image");
    }


//    /**
//     * 实际生成图片的方法
//     * @param prompt 图像描述
//     * @return 生成结果
//     */
//    private String generateImageWithUser(String prompt) {
//        try {
//            log.info("Generating image with prompt: {}", prompt);
//
//            // 构建符合API要求的请求体
//            // model备选：首选qwen-image-plus。wan2.2-t2i-plus、wan2.2-t2i-flash、wan2.2-t2i-flash。低成本：wanx2.0-t2i-turbo
//            String requestBody = String.format("""
//        {
//            "model": "qwen-image-plus",
//            "input": {
//                "messages": [
//                    {
//                        "role": "user",
//                        "content": [
//                            {
//                                "text": "%s"
//                            }
//                        ]
//                    }
//                ]
//            },
//            "parameters": {
//                "n": 1,
//                "size": "1328*1328",
//                "prompt_extend": true,
//                "watermark": false
//            }
//        }
//        """, prompt.replace("\"", "\\\""));
//
//            log.debug("Request body: {}", requestBody);
//
//            // 创建 HTTP 客户端
//            HttpClient client = HttpClient.newBuilder()
//                    .connectTimeout(Duration.ofSeconds(30))
//                    .build();
//
//            // 创建请求
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(IMAGE_GENERATION_URL))
//                    .header("Authorization", "Bearer " + apiKey)
//                    .header("Content-Type", "application/json")
//                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
//                    .build();
//
//            // 发送请求
//            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//
//            // 检查响应状态码
//            int statusCode = response.statusCode();
//            String responseBody = response.body();
//            log.info("Image generation response status: {}, body: {}", statusCode, responseBody);
//
//            if (statusCode >= 200 && statusCode < 300) {
//                try {
//                    // 解析JSON响应
//                    JSONObject jsonObject = JSONUtil.parseObj(responseBody);
//
//                    // 尝试提取image URL
//                    if (jsonObject.containsKey("output")) {
//                        JSONObject output = jsonObject.getJSONObject("output");
//                        if (output.containsKey("choices") && !output.getJSONArray("choices").isEmpty()) {
//                            JSONObject choice = output.getJSONArray("choices").getJSONObject(0);
//                            if (choice.containsKey("message")) {
//                                JSONObject message = choice.getJSONObject("message");
//                                if (message.containsKey("content") && !message.getJSONArray("content").isEmpty()) {
//                                    JSONObject content = message.getJSONArray("content").getJSONObject(0);
//                                    if (content.containsKey("image")) {
//                                        // 获取image字段值
//                                        String imageField = content.getStr("image");
//                                        // 清理URL：移除反引号和空白字符
//                                        String cleanImageUrl = imageField.replaceAll("[`\\s]", "");
//                                        log.info("Extracted and cleaned image URL: {}", cleanImageUrl);
//                                        return cleanImageUrl;
//                                    }
//                                }
//                            }
//                        }
//                    }
//
//                    // 如果无法提取image URL，返回原始响应作为后备
//                    log.warn("Could not extract image URL from response");
//                    return responseBody;
//                } catch (Exception e) {
//                    log.error("Error parsing response JSON: {}", e.getMessage());
//                    // 解析失败时返回原始响应
//                    return responseBody;
//                }
//            } else {
//                log.error("Image generation failed with status code: {}, response: {}",
//                        statusCode, responseBody);
//
//                return "图片生成失败: " + ResultUtils.error(ErrorCode.OPERATION_ERROR, responseBody);
//            }
//        } catch (Exception e) {
//            log.error("Failed to generate image", e);
//
//            return "图片生成异常: " + e.getMessage();
//        }
//    }
}