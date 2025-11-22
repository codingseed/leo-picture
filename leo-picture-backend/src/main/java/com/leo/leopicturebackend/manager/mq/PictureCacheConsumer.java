package com.leo.leopicturebackend.manager.mq;

import com.leo.leopicturebackend.config.RabbitConfig;
import com.leo.leopicturebackend.manager.LocalCacheManager;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
/*
* 通过消息确认机制确保消息不丢失
    手动ACK/NACK机制保证消息被正确处理
    重试机制提高系统稳定性*/
@Component
@Slf4j
public class PictureCacheConsumer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private LocalCacheManager localCacheManager;

    /*
    Channel
    消息确认控制：用于手动确认消息处理状态
    连接管理：代表与 RabbitMQ 服务器的通信通道
    操作执行：执行如 basicAck、basicNack 等底层操作
    DELIVERY_TAG
    消息唯一标识：每个消息在通道中的唯一数字标识符
    确认机制关键：用于向 RabbitMQ 确认特定消息的处理状态
    顺序保证：确保消息按正确顺序确认
    */
    @RabbitListener(queues = RabbitConfig.PICTURE_UPDATE_QUEUE)
    public void handlePictureUpdate(Map<String, Object> message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            Long pictureId = Long.valueOf(message.get("pictureId").toString());
            String operation = (String) message.get("operation");

            // 删除相关缓存
            String cacheKey = "leopicture:listPictureVOByPage:*";
            Set<String> keys = stringRedisTemplate.keys(cacheKey);
            // 清除本地缓存（双缓存一致性）
            localCacheManager.clearCacheByPattern(cacheKey);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }

            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.info("成功处理图片{}更新消息，图片ID: {}", operation,pictureId);
        } catch (Exception e) {
            log.error("处理图片更新消息失败", e);
            try {
                // 当达到最大重试次数后，消息会自动进入死信队列
                // 这里我们拒绝消息，让它可以被重新排队（或进入死信队列）
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ioException) {
                log.error("消息拒绝失败", ioException);
            }
        }
    }
}
