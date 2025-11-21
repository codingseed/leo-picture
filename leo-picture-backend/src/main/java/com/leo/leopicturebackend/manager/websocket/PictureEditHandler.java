package com.leo.leopicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.leo.leopicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.leo.leopicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.leo.leopicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.leo.leopicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.leo.leopicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.leo.leopicturebackend.model.entity.User;
import com.leo.leopicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片编辑 WebSocket 处理器
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    /*
    * 一个“编辑锁”：这个Map记录着每张图片的“编辑令牌”在谁手里。
    * 只有持有令牌的用户才能执行编辑操作（handleEditActionMessage中会检查），
    * 这完美解决了多人同时编辑的冲突问题。一种乐观锁在应用层的实现
    * Map<Long, Long> pictureEditingUsers
            Key: pictureId (图片ID)
            Value: userId (用户ID)
            业务含义：记录某张图片当前被谁编辑。
            存储结构：
            假设有 pictureId=1 的图片正在被 userId=100 的用户编辑
            那么在Map中就是：1 -> 100
            同一个pictureId只能对应一个userId，因为一张图片同一时刻只能有一个人编辑(独占锁写锁)*/
    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();
    /*
       * 一个分组广播模型。以pictureId为组，管理所有订阅了该图片编辑状态的会话。
         当需要广播时，可以直接从Map中取出目标Set进行高效推送，避免了遍历所有连接的巨大开销
         * Map<Long, Set<WebSocketSession>> pictureSessions
               Key: pictureId (图片ID)
               Value: Set<WebSocketSession> (WebSocket会话集合)
               业务含义：记录正在观看某张图片的所有用户连接。
               存储结构：
               假设有3个用户都在观看 pictureId=1 的图片
               那么在Map中就是：1 -> [sessionA, sessionB, sessionC]
               同一个pictureId对应一个包含多个session的Set(共享锁读锁)*/
    // 保存所有连接的会话，key: pictureId, value: 用户会话集合，不会重复。
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    /**
     * 连接建立成功后处理
     *
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        // 保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);
        // 构造响应，发送加入编辑的消息通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 广播给所有用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 收到前端发送的消息，根据消息类别处理消息
     *
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        // 获取消息内容，将 JSON 转换为 PictureEditRequestMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 从 Session 属性中获取到公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 根据消息类型处理消息（生产消息到 Disruptor 环形队列中）
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }



    /**
     * 进入编辑状态
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置用户正在编辑该图片
            pictureEditingUsers.putIfAbsent(pictureId, user.getId());
            // 构造响应，发送加入编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("用户 %s 开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给所有用户
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
        // 如果已经有用户在编辑了，那么就将该用户广播给请求进行编辑的用户
        // 通知其它用户
        ObjectMapper objectMapper = new ObjectMapper();
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        // 已经有用户在编辑，获取当前编辑用户ID
        Long editingUserId = pictureEditingUsers.get(pictureId);
        User editingUser = userService.getById(editingUserId);
        String message = String.format("用户 %s 正在编辑", editingUser.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
        pictureEditResponseMessage.setUser(userService.getUserVO(editingUser));
        // 单独发送消息给当前请求编辑的session
        String str = objectMapper.writeValueAsString(pictureEditResponseMessage);
        TextMessage textMessage = new TextMessage(str);
        if (session.isOpen()) {
            session.sendMessage(textMessage);
        }
    }

    /**
     * 处理编辑动作消息
     *
     * @param pictureEditRequestMessage 图片编辑请求消息，包含编辑动作等信息
     * @param session WebSocket会话，用于通信
     * @param user 当前操作用户
     * @param pictureId 被编辑的图片ID
     * @throws Exception 处理过程中可能抛出的异常
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        //获取当前图片的编辑者，从编辑该图片的用户集合中获取，使用currenHashmap避免冲突
        Long editingUserId = pictureEditingUsers.get(pictureId);
        //获取编辑动作
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 构造响应信息后广播发送
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 处理退出编辑图片的消息
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session WebSocket会话
     * @param user 当前操作用户
     * @param pictureId 图片ID
     * @throws Exception 处理过程中可能抛出的异常
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 正在编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        // 确认是当前的编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除正在编辑该图片的用户
            pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("用户 %s 退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * 处理获取当前编辑状态的消息
     * //t
     * 该方法用于查询指定图片当前被哪个用户编辑，并将该用户的信息返回给请求者。
     * 主要处理WebSocket会话中的图片编辑状态查询请求。
     * //r//n
     * @param pictureEditRequestMessage 图片编辑请求消息，包含请求的具体信息
     * @param session WebSocket会话，用于与客户端进行通信
     * @param user 当前请求用户信息
     * @param pictureId 要查询的图片ID
     * @throws IOException 当发送消息过程中发生I/O错误时抛出
     */
    public void handleGetCurrentEditStatusMessage(PictureEditRequestMessage pictureEditRequestMessage,
                                                  WebSocketSession session, User user, Long pictureId) throws IOException {
        // 获取当前正在编辑该图片的用户ID
        Long editingUserId = pictureEditingUsers.get(pictureId);
        User editUser = userService.getById(editingUserId);
        // 构造响应消息
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.CURRENT_EDIT_STATUS.getValue());
        pictureEditResponseMessage.setUser(userService.getUserVO(editUser));
        // 发送响应给请求者
        ObjectMapper objectMapper = new ObjectMapper();
        // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(simpleModule);

        String s = objectMapper.writeValueAsString(pictureEditResponseMessage);
        if (session.isOpen()){
            session.sendMessage(new TextMessage(s));
        }
    }

    /**
     * 关闭连接
     *
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        // 从 Session 属性中获取到公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);
        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }
        // 通知其他用户，该用户已经离开编辑
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 广播给该图片的所有用户（支持排除掉某个 Session）
     * 该方法用于向所有正在查看指定图片的用户发送编辑响应消息，可以排除特定会话
     * @param pictureId 图片ID，用于定位对应的用户会话集合
     * @param pictureEditResponseMessage 要发送的图片编辑响应消息对象
     * @param excludeSession 需要排除的WebSocket会话，如果为null则不排除任何会话
     * @throws Exception 可能抛出的异常，如消息序列化或发送异常
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws IOException {
        // 根据pictureId获取所有相关的WebSocket会话集合
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        // 检查会话集合是否为空
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 创建 ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // Websocket配置序列化：将 Long 类型转为 String，解决丢失精度问题
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
            objectMapper.registerModule(module);
            // 序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除掉的 session 不发送
                if (excludeSession != null && session.equals(excludeSession)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    /**
     * 广播给该图片的所有用户
     *
     * @param pictureId
     * @param pictureEditResponseMessage
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws IOException {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }
}
