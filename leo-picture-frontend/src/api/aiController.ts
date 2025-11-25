import request from '@/request';

/**
 * AI助手控制器
 * 用于处理AI聊天相关的API调用
 */

/**
 * 创建EventSource连接进行SSE通信
 * @param memoryId 会话记忆ID
 * @param message 用户消息
 * @param onMessage 消息接收回调
 * @param onOpen 连接打开回调
 * @param onError 错误处理回调
 * @param onClose 连接关闭回调
 * @returns EventSource实例
 */
export const createChatEventSource = (
  memoryId: number,
  message: string,
  onMessage: (data: string) => void,
  onOpen?: () => void,
  onError?: (error: Event) => void,
  onClose?: () => void
): EventSource => {
  const url = `http://localhost:8123/api/ai/chat?memoryId=${memoryId}&message=${encodeURIComponent(message)}`;
  const eventSource = new EventSource(url, { withCredentials: true });
  
  eventSource.onmessage = (event) => {
    onMessage(event.data);
  };
  
  if (onOpen) {
    eventSource.onopen = onOpen;
  }
  
  if (onError) {
    eventSource.onerror = onError;
  }
  
  if (onClose) {
    eventSource.onclose = onClose;
  }
  
  return eventSource;
};

/**
 * 通过Axios获取AI聊天响应（非SSE方式）
 * 备用方法，当SSE不可用时可以使用
 * @param memoryId 会话记忆ID
 * @param message 用户消息
 * @returns Promise<string>
 */
export const getChatResponse = async (memoryId: number, message: string): Promise<string> => {
  const response = await request.get('/ai/chat', {
    params: {
      memoryId,
      message
    }
  });
  return response.data;
};

/**
 * 创建新的AI会话
 * @returns Promise<{ sessionId: string; memoryId: number }>
 */
export const createNewSession = async (): Promise<{ sessionId: string; memoryId: number }> => {
  try {
    // 这里可以扩展为调用后端API创建会话
    // 目前使用前端生成的方式
    const sessionId = 'chat_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    const memoryId = parseInt(sessionId.replace(/\D/g, '')) || 1;
    
    return {
      sessionId,
      memoryId
    };
  } catch (error) {
    console.error('创建新会话失败:', error);
    // 降级处理：使用纯前端生成
    const sessionId = 'chat_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    const memoryId = parseInt(sessionId.replace(/\D/g, '')) || 1;
    return { sessionId, memoryId };
  }
};

/**
 * 获取会话历史（如果后端支持的话）
 * @param memoryId 会话记忆ID
 * @returns Promise<Array<{ role: 'user' | 'assistant'; content: string }>>
 */
export const getSessionHistory = async (memoryId: number): Promise<Array<{ role: 'user' | 'assistant'; content: string }>> => {
  try {
    // 这里可以扩展为调用后端API获取历史记录
    // 目前返回空数组
    return [];
  } catch (error) {
    console.error('获取会话历史失败:', error);
    return [];
  }
};

export default {
  createChatEventSource,
  getChatResponse,
  createNewSession,
  getSessionHistory
};