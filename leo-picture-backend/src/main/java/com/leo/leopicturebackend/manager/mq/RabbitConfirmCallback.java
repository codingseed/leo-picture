package com.leo.leopicturebackend.manager.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/*
* 处理消息确认回调
*
当消息成功发送到 RabbitMQ Broker 时调用
或者发送失败时也会调用（ack 参数为 false）*/
@Slf4j
@Component
public class RabbitConfirmCallback implements RabbitTemplate.ConfirmCallback {

    /*
    * correlationData: 关联数据对象，用于关联发送的消息和确认结果
                        包含唯一标识符，可用于追踪特定消息
                        可以携带自定义数据
    ack: 确认标志
        true: 消息成功发送到 RabbitMQ Broker
        false: 消息发送失败
    cause: 失败原因
        当 ack 为 false 时，包含具体的失败原因描述
        成功时为 null*/
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack){
            log.info("消息发送成功{}",correlationData.getId());
        }else {
            log.info("消息发送失败{},原因是{}",correlationData.getId(),cause);
        }

    }
}
