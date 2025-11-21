package com.leo.leopicturebackend.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* 定义交换机、队列及绑定关系：*/
/*
数据变更时通过 RabbitTemplate 发送消息通知
消费端 PictureCacheConsumer 监听并清理相关Redis缓存
实现数据库与缓存的最终一致性
将缓存清理逻辑从主业务流程中分离，
数据操作服务无需直接处理缓存更新逻辑
清理在后台异步执行，不阻塞主业务流程
* */
@Configuration
public class RabbitConfig {
    /*
    *
        因为删除和更新操作都涉及数据变更，
        两者都需要清除相关的分页查询缓存，这里直接使用一套交换机和队列，易于维护和监控*/
    // 图片更新/删除的 Exchange 和 Queue。
    public static final String PICTURE_UPDATE_EXCHANGE = "picture.update.exchange";
    public static final String PICTURE_UPDATE_QUEUE = "picture.update.queue";
    public static final String PICTURE_UPDATE_ROUTING_KEY = "picture.update.routingkey";

    // 图片审核的 Exchange 和 Queue，目前先不用
    public static final String PICTURE_REVIEW_EXCHANGE = "picture.review.exchange";
    public static final String PICTURE_REVIEW_QUEUE = "picture.review.queue";
    public static final String PICTURE_REVIEW_ROUTING_KEY = "picture.review.routingkey";

    // 死信交换机和队列配置
    public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String DLX_QUEUE = "dlx.queue";
    public static final String DLX_ROUTING_KEY = "dlx.routingkey";

    @Bean
    public DirectExchange pictureUpdateExchange() {
        return new DirectExchange(PICTURE_UPDATE_EXCHANGE);
    }

    @Bean
    public Queue pictureUpdateQueue() {
        return QueueBuilder.durable(PICTURE_UPDATE_QUEUE)//durable方法会将队列设置为持久化队列
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .withArgument("x-message-ttl", 10000) // 设置消在队列中的过期时间为10秒
        .build();
    }

    @Bean
    public Binding pictureUpdateBinding() {
        return BindingBuilder.bind(pictureUpdateQueue())
                .to(pictureUpdateExchange())
                .with(PICTURE_UPDATE_ROUTING_KEY);
    }

    @Bean
    public DirectExchange pictureReviewExchange() {
        return new DirectExchange(PICTURE_REVIEW_EXCHANGE);
    }

    @Bean
    public Queue pictureReviewQueue() {
        return new Queue(PICTURE_REVIEW_QUEUE);
    }

    @Bean
    public Binding pictureReviewBinding() {
        return BindingBuilder.bind(pictureReviewQueue())
                .to(pictureReviewExchange())
                .with(PICTURE_REVIEW_ROUTING_KEY);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with(DLX_ROUTING_KEY);
    }
}


