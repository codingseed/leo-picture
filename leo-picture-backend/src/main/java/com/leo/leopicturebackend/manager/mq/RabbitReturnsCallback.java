package com.leo.leopicturebackend.manager.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/*
* 处理消息返回回调
当消息无法被路由到任何队列时调用
例如：交换机找不到、路由键匹配失败等情况/
 */
@Slf4j
@Component
public class RabbitReturnsCallback implements RabbitTemplate.ReturnsCallback {
    @Override
    public void returnedMessage(ReturnedMessage returned) {
/*returned: 返回的消息对象，包含以下信息：
    getExchange(): 消息发送的目标交换机名称
    getRoutingKey(): 使用的路由键
    getReplyCode(): 返回的状态码
    getReplyText(): 返回的文本描述
    getMessage(): 原始消息内容
* */
        log.warn("消息被退回: exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText());
    }
}
