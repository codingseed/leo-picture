// @ts-ignore
/* eslint-disable */
import request from '@/request'

/**
 * 创建聊天的EventSource连接（简化版本）
 * @param message 用户消息
 * @param chatId 会话ID
 * @returns EventSource实例
 */
export function createChatEventSource(message: string, chatId: string): EventSource {
  // 从localStorage获取登录用户信息
  const loginUserJson = localStorage.getItem('loginUser')
  let tokenValue = ''

  // 解析存储的用户信息并获取token
  if (loginUserJson) {
    try {
      const loginUser = JSON.parse(loginUserJson)

      // 尝试多种方式获取token
      if (loginUser.token) {
        tokenValue = loginUser.token
      } else if (loginUser.tokenValue) {
        tokenValue = loginUser.tokenValue
      } else if (loginUser.satoken) {
        tokenValue = loginUser.satoken
      } else if (loginUser.Authorization) {
        tokenValue = loginUser.Authorization
      }
    } catch (e) {
      console.error('解析loginUser失败:', e)
    }
  }

  // 构建SSE连接URL
  let url = `${process.env.NODE_ENV === 'development' ? 'http://localhost:8123' : 'http://pic.codingseed.site'}/api/ai/chat?message=${encodeURIComponent(message)}&chatId=${chatId}`

  // 添加token参数
  if (tokenValue) {
    url += `&token=${encodeURIComponent(tokenValue)}`
  }

  console.log('构建的SSE URL:', url)

  // 创建并返回EventSource实例
  const eventSource = new EventSource(url, { withCredentials: true })

  // 设置基本的事件处理
  eventSource.onopen = () => {
    console.log('SSE连接已建立')
  }

  eventSource.onerror = (error) => {
    console.error('SSE连接错误:', error)
  }

  return eventSource
}

/** createChat POST /api/ai/chat */
export async function createChatUsingPost(
  body: API.CreateChatRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseString_>('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
