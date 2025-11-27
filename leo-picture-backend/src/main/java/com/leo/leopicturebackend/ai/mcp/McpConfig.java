package com.leo.leopicturebackend.ai.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class McpConfig {

    @Value("${bigmodel.api-key}")
    private  String key;

//    @Value("${bigmodel.api-qwkey:}") // 设置默认值为空字符串
//    private  String qwKey;
    @Bean
    public McpToolProvider mcpToolProvider() {
        try {
            //1,MCP 传输.首先，你需要一个 MCP 传输实例。就是使用别人的 MCP 服务器，创建一个 MCP 传输实例(HTTP链接之类)。
            McpTransport transport = new HttpMcpTransport.Builder()
                    .timeout(Duration.ofSeconds(300)) // 增加超时时间到5分钟
                    .sseUrl("https://open.bigmodel.cn/api/mcp/web_search/sse?Authorization="+key)// 设置 SSE URL智谱模型，功能为让AI实现全网搜索内容
                    .logRequests(true) // 打印请求
                    .logResponses(true) // 打印响应
                    .build();

            //2.MCP 客户端,从传输创建 MCP 客户端：
            McpClient mcpClient = new DefaultMcpClient.Builder()
                    .transport(transport)
                    .build();

            //3.MCP 工具提供者,最后，从客户端创建 MCP 工具提供者：
            McpToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(List.of(mcpClient))
                    .build();
            return toolProvider;
        } catch (Exception e) {
            // 如果MCP配置失败，返回一个空的工具提供者
            System.err.println("MCP配置失败: " + e.getMessage());
            return McpToolProvider.builder().build();
        }
     }

}