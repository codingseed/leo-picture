package com.leo.leopicturebackend.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.Disruptor;
import com.leo.leopicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.leo.leopicturebackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 Disruptor图片编辑事件生产者
 负责将数据（事件）发到 Disruptor 的环形缓冲区中。
 为了保证在停机时所有的消息都能够被处理，我们通过 shutdown 方法完成 Disruptor 的优雅停机

 事件驱动架构的完整流程
 ┌─────────────────┐    ┌──────────────────┐    ┌────────────────────┐    ┌─────────────────┐
 │  事件生产者      │    │  事件通道         │    │  事件消费者         │    │  事件处理器      │
 │ (WebSocket消息) │───▶│ (Disruptor队列)  │───▶│ (WorkHandler)     │───▶│ (业务处理方法)   │
 └─────────────────┘    └──────────────────┘    └────────────────────┘    └─────────────────┘

 */
@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 发布事件
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 获取到可以防止事件的位置
        long next = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     * Spring Bean 生命周期：
     * 1. 实例化 → 2. 属性注入 → 3. 初始化 → 4. 使用中 → 5. 销毁
     */
    @PreDestroy//在bean生命周期第5阶段执行：
    public void close() {
        try {
            if (pictureEditEventDisruptor != null) {
                // 设置超时时间，避免无限等待
                pictureEditEventDisruptor.shutdown(30, TimeUnit.SECONDS);
                log.info("Disruptor 已使用@PreDestroy优雅关闭");
            }
        } catch (TimeoutException e) {
            log.warn("Disruptor 关闭超时，强制关闭", e);
            pictureEditEventDisruptor.halt();
        } catch (Exception e) {
            log.error("关闭 Disruptor 时发生异常", e);
        }
    }
}

