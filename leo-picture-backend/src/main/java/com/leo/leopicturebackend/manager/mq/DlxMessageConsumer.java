package com.leo.leopicturebackend.manager.mq;

import com.leo.leopicturebackend.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DlxMessageConsumer {

    @RabbitListener(queues = RabbitConfig.DLX_QUEUE)
    public void handleDlxMessage(Message message) {
        try {
            // 记录失败消息到数据库
            log.error("消息进入死信队列: {}", new String(message.getBody()));
            // TODO: 保存到数据库进行后续补偿处理
        } catch (Exception e) {
            log.error("处理死信消息失败", e);
        }
    }
}

