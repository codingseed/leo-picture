<template>
  <div class="ai-assistant-container">
    <!-- 聊天记录区域 -->
    <div class="chat-messages-container">
      <div class="chat-header">
        <h2>云梦图坊AI助手</h2>
        <div class="header-actions">
          <div class="session-info">会话 ID: {{ sessionId }}</div>
          <button @click="resetSession" class="new-chat-button">新会话</button>
        </div>
      </div>

      <div class="messages-wrapper" ref="messagesWrapper">
        <!-- 聊天消息列表 -->
        <div
          v-for="(message, index) in messages"
          :key="index"
          :class="['message-item', message.type === 'user' ? 'user-message' : 'ai-message']"
        >
          <div class="message-avatar">
            <img
              :src="message.type === 'user' ? '/favicon.ico' : '/favicon.ico'"
              :alt="message.type === 'user' ? '用户' : 'AI'"
              class="avatar-img"
            />
          </div>
          <div class="message-content">
            <div class="message-bubble">
              <!-- 使用v-html渲染分段内容 -->
              <div class="message-text" v-html="formatMessageContent(message.content)"></div>
              <span v-if="message.type === 'ai-partial'" class="typing-indicator-small">
                <span></span>
                <span></span>
                <span></span>
              </span>
            </div>
          </div>
        </div>

        <!-- AI 正在输入状态 -->
        <div v-if="isTyping" class="message-item ai-message">
          <div class="message-avatar">
            <img src="/favicon.ico" alt="AI" class="avatar-img" />
          </div>
          <div class="message-content">
            <div class="message-bubble typing">
              <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入框区域 -->
    <div class="chat-input-container">
      <textarea
        v-model="inputMessage"
        @keyup.enter.ctrl="sendMessage"
        @keyup.enter.meta="sendMessage"
        placeholder="请输入你的问题..."
        class="chat-textarea"
        ref="textareaRef"
      ></textarea>
      <button @click="sendMessage" :disabled="!inputMessage.trim() || isTyping" class="send-button">
        发送
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted } from 'vue'
import { createChatEventSource } from '@/api/aiController'
// 移除未使用的导入
// import { useLoginUserStore } from '@/stores/useLoginUserStore';
// import { message } from 'ant-design-vue';

// 会话和消息状态
const sessionId = ref<string>('')

// 生成会话 ID
const generateSessionId = (): string => {
  return 'chat_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9)
}
const messages = ref<Array<{ type: 'user' | 'ai' | 'ai-partial'; content: string }>>([])
const inputMessage = ref('')
const isTyping = ref(false)
const messagesWrapper = ref<HTMLElement>()
const textareaRef = ref<HTMLTextAreaElement>()
let currentAIResponse = ''
let eventSource: EventSource | null = null
// 移除未使用的变量
// const loginUserStore = useLoginUserStore();

// 从本地存储加载会话
const loadSessionFromStorage = () => {
  try {
    // 尝试从URL获取会话ID
    const urlParams = new URLSearchParams(window.location.search)
    const urlSessionId = urlParams.get('sessionId')

    let sessionData = null
    let targetSessionId = urlSessionId

    // 如果URL中有会话ID，尝试加载该会话
    if (targetSessionId) {
      sessionData = localStorage.getItem(`ai_chat_session_${targetSessionId}`)
    }

    // 如果没有找到会话或URL中没有会话ID，尝试加载最后一个会话
    if (!sessionData) {
      const lastSessionId = localStorage.getItem('ai_chat_last_session_id')
      if (lastSessionId) {
        sessionData = localStorage.getItem(`ai_chat_session_${lastSessionId}`)
        targetSessionId = lastSessionId
      }
    }

    if (sessionData) {
      const parsedData = JSON.parse(sessionData)
      sessionId.value = parsedData.sessionId
      messages.value = parsedData.messages || []
      return true
    }
  } catch (error) {
    console.error('加载会话失败:', error)
  }

  return false
}

// 保存会话到本地存储
const saveSessionToStorage = () => {
  try {
    const sessionData = {
      sessionId: sessionId.value,
      messages: messages.value,
      timestamp: Date.now(),
    }

    // 保存会话数据
    localStorage.setItem(`ai_chat_session_${sessionId.value}`, JSON.stringify(sessionData))
    // 更新最后会话ID
    localStorage.setItem('ai_chat_last_session_id', sessionId.value)

    // 清理旧会话（保留最近5个）
    cleanupOldSessions()
  } catch (error) {
    console.error('保存会话失败:', error)
  }
}

// 清理旧会话
const cleanupOldSessions = () => {
  try {
    const sessions = []

    // 收集所有会话
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key && key.startsWith('ai_chat_session_')) {
        const sessionData = localStorage.getItem(key)
        if (sessionData) {
          const parsed = JSON.parse(sessionData)
          sessions.push({ key, timestamp: parsed.timestamp })
        }
      }
    }

    // 按时间排序并删除最旧的会话
    sessions.sort((a, b) => b.timestamp - a.timestamp)
    while (sessions.length > 5) {
      const oldestSession = sessions.pop()
      if (oldestSession) {
        localStorage.removeItem(oldestSession.key)
      }
    }
  } catch (error) {
    console.error('清理旧会话失败:', error)
  }
}

// 重置会话
const resetSession = async () => {
  // 关闭当前活跃的SSE连接
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  // 重置输入状态
  isTyping.value = false
  currentAIResponse = ''

  // 生成新会话ID
  sessionId.value = generateSessionId()

  messages.value = []
  // 添加欢迎消息
  messages.value.push({
    type: 'ai',
    content: `你好！我是你的智能云图库助手，可以帮你更好地使用我们的在线协同云图库平台。

    我可以帮助你：
    • 上传和管理图片资源
    • 搜索和查找特定图片
    • 与团队成员协作编辑
    • 使用图库的各种功能特性
    • 解决你在使用过程中遇到的问题

    请问有什么我可以帮你的吗？`,
  })
  saveSessionToStorage()
}

// 发送消息
const sendMessage = async () => {
  const message = inputMessage.value.trim()
  if (!message || isTyping.value) return

  // 添加用户消息到列表
  messages.value.push({ type: 'user', content: message })
  inputMessage.value = ''

  // 保存会话
  saveSessionToStorage()

  // 滚动到底部
  await nextTick()
  scrollToBottom()

  // 开始AI回复
  isTyping.value = true
  currentAIResponse = ''

  // 先创建一个占位的AI消息（使用ai-partial类型）
  const aiMessageIndex = messages.value.length
  messages.value.push({ type: 'ai-partial', content: '' })

  try {
    // 重要：发送新消息前关闭可能存在的旧SSE连接
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }

    // 使用简化后的API创建SSE连接
    eventSource = createChatEventSource(message, sessionId.value)

    // 设置消息处理
    eventSource.onmessage = (event) => {
      console.log('收到SSE消息:', event.data)
      // 检查是否是结束信号
      if (event.data === '[DONE]') {
        // 完成时关闭连接并更新状态
        isTyping.value = false
        eventSource.close()
        eventSource = null

        // 将ai-partial类型更新为ai类型
        if (
          aiMessageIndex < messages.value.length &&
          messages.value[aiMessageIndex].type === 'ai-partial'
        ) {
          messages.value[aiMessageIndex].type = 'ai'
          saveSessionToStorage()
        }

        nextTick(() => scrollToBottom())
      } else {
        currentAIResponse += event.data
        // 直接更新之前创建的AI消息
        if (aiMessageIndex < messages.value.length) {
          messages.value[aiMessageIndex].content = currentAIResponse
          nextTick(() => scrollToBottom())
        }
      }
    }

    // 设置错误处理
    eventSource.onerror = (error) => {
      console.error('SSE连接错误:', error)
      handleSSEError()
    }

    // 设置连接关闭处理
    eventSource.onclose = () => {
      console.log('SSE连接已关闭')
      if (isTyping.value) {
        isTyping.value = false
        nextTick(() => scrollToBottom())
      }
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    handleSSEError()
  }
}

// 处理SSE错误
const handleSSEError = () => {
  isTyping.value = false
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  // 检查最后一条消息是否是ai-partial类型
  const lastIndex = messages.value.length - 1
  if (lastIndex >= 0 && messages.value[lastIndex].type === 'ai-partial') {
    // 更新现有的ai-partial消息
    if (currentAIResponse) {
      messages.value[lastIndex].type = 'ai'
      messages.value[lastIndex].content = currentAIResponse
    } else {
      messages.value[lastIndex].type = 'ai'
      messages.value[lastIndex].content = '抱歉，连接出现问题，请稍后重试。'
    }
  }

  saveSessionToStorage()
  nextTick(() => scrollToBottom())
}

// 移除handleSSEComplete函数，因为我们不再需要它

// 删除updateAIResponse函数，因为我们已经在sendMessage中直接处理消息更新

// 滚动到底部
// 格式化消息内容，支持分段和简单Markdown样式
const formatMessageContent = (content: string): string => {
  if (!content) return ''

  // 替换段落分隔（连续换行）
  let formattedContent = content.replace(/\n\n+/g, '<br><br>')

  // 替换单换行
  formattedContent = formattedContent.replace(/\n/g, '<br>')

  // 简单Markdown支持：加粗（**内容**）
  formattedContent = formattedContent.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')

  // 简单Markdown支持：斜体（*内容*）
  formattedContent = formattedContent.replace(/\*(.*?)\*/g, '<em>$1</em>')

  // 简单Markdown支持：标题（# 标题）
  formattedContent = formattedContent.replace(/^###\s+(.*?)$/gm, '<h3>$1</h3>')
  formattedContent = formattedContent.replace(/^##\s+(.*?)$/gm, '<h2>$1</h2>')
  formattedContent = formattedContent.replace(/^#\s+(.*?)$/gm, '<h1>$1</h1>')

  return formattedContent
}

const scrollToBottom = () => {
  if (messagesWrapper.value) {
    messagesWrapper.value.scrollTop = messagesWrapper.value.scrollHeight
  }
}

// 组件卸载时清理资源
onUnmounted(() => {
  // 确保关闭SSE连接，防止内存泄漏
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})

// 组件挂载时初始化
onMounted(() => {
  // 尝试加载已有会话，如果没有则创建新会话
  if (!loadSessionFromStorage()) {
    // 生成新会话ID
    sessionId.value = generateSessionId()
    // 添加欢迎消息
    messages.value.push({
      type: 'ai',
      content:
        '你好！我是AI编程小助手，可以帮我解答编程学习和求职面试相关的问题。请问有什么我可以帮助你的吗？',
    })
    // 保存新会话
    saveSessionToStorage()
  }

  // 聚焦输入框
  if (textareaRef.value) {
    textareaRef.value.focus()
  }

  // 滚动到底部
  nextTick(() => scrollToBottom())
})
</script>

<!-- 全局样式已移至App.vue -->

<style scoped>
.ai-assistant-container {
  display: flex;
  flex-direction: column;
  height: 80vh; /* 缩小对话框高度 */
  max-width: 1000px;
  margin: 20px auto;
  background-color: #f5f5f5;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.chat-header {
  background-color: #fff;
  padding: 16px 20px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border-top-left-radius: 16px;
  border-top-right-radius: 16px;
}

.chat-header h2 {
  margin: 0;
  color: #1890ff;
  font-size: 20px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.session-info {
  color: #999;
  font-size: 12px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-chat-button {
  padding: 6px 12px;
  background-color: #f0f0f0;
  color: #666;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.new-chat-button:hover {
  background-color: #e0e0e0;
  color: #333;
}

.chat-messages-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.messages-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 为消息区域添加特殊滚动条样式 - 覆盖全局隐藏样式 */
.messages-wrapper::-webkit-scrollbar {
  display: block;
  width: 8px;
}

.messages-wrapper::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

.messages-wrapper::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 4px;
}

.messages-wrapper::-webkit-scrollbar-thumb:hover {
  background: #555;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.user-message {
  flex-direction: row-reverse;
}

.ai-message {
  flex-direction: row;
}

.message-avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.message-content {
  max-width: 70%;
}

.user-message .message-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 8px;
  word-wrap: break-word;
  line-height: 1.5;
}

.message-text {
  white-space: pre-line;
}

/* Markdown样式支持 */
.message-text h1 {
  font-size: 1.5em;
  margin-top: 0.5em;
  margin-bottom: 0.5em;
}

.message-text h2 {
  font-size: 1.3em;
  margin-top: 0.5em;
  margin-bottom: 0.5em;
}

.message-text h3 {
  font-size: 1.1em;
  margin-top: 0.5em;
  margin-bottom: 0.5em;
}

.message-text strong {
  font-weight: bold;
}

.message-text em {
  font-style: italic;
}

.user-message .message-bubble {
  background-color: #1890ff;
  color: white;
  border-bottom-right-radius: 4px;
}

.ai-message .message-bubble {
  background-color: white;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.typing-indicator {
  display: flex;
  gap: 4px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background-color: #999;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator-small {
  display: inline-flex;
  gap: 3px;
  margin-left: 6px;
}

.typing-indicator-small span {
  width: 6px;
  height: 6px;
  background-color: #666;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%,
  80%,
  100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.chat-input-container {
  background-color: white;
  padding: 16px 20px;
  border-top: 1px solid #e8e8e8;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  border-bottom-left-radius: 16px;
  border-bottom-right-radius: 16px;
}

.chat-textarea {
  flex: 1;
  min-height: 60px;
  max-height: 120px;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  resize: vertical;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
}

.chat-textarea:focus {
  outline: none;
  border-color: #40a9ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.send-button {
  padding: 12px 24px;
  background-color: #1890ff;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  white-space: nowrap;
}

.send-button:hover:not(:disabled) {
  background-color: #40a9ff;
}

.send-button:disabled {
  background-color: #f5f5f5;
  color: #bfbfbf;
  cursor: not-allowed;
}

/* 滚动条样式 */
.messages-wrapper::-webkit-scrollbar {
  width: 6px;
}

.messages-wrapper::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.messages-wrapper::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.messages-wrapper::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .ai-assistant-container {
    height: 100vh;
  }

  .message-content {
    max-width: 85%;
  }

  .chat-header {
    padding: 12px 16px;
  }

  .chat-header h2 {
    font-size: 18px;
  }

  .messages-wrapper {
    padding: 16px;
  }

  .chat-input-container {
    padding: 12px 16px;
  }
}
</style>
