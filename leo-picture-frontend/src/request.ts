import axios from 'axios'
import { message } from 'ant-design-vue'

// 区分开发和生产环境
const DEV_BASE_URL = 'http://localhost:8123'
const PROD_BASE_URL = 'http://pic.codingseed.site'
// 创建 Axios 实例
const myAxios = axios.create({
  // 使用import.meta.env来判断环境，这是Vite的标准方式
  baseURL: import.meta.env.DEV ? DEV_BASE_URL : PROD_BASE_URL,
  timeout: 60000,
  withCredentials: true,
})

// 定义登录用户信息的接口
interface LoginUser {
  tokenName?: string
  tokenValue?: string
  // 可以添加其他用户相关字段，如用户名、权限等
}

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // 从localStorage获取登录用户信息
    const loginUserJson = localStorage.getItem('loginUser')
    
    // 解析存储的用户信息（防止解析失败）
    if (loginUserJson) {
      try {
        const loginUser = JSON.parse(loginUserJson)
        // 使用类型断言避免类型错误
        const userObj = loginUser as any
        
        // 检查是否有tokenName和tokenValue字段（与后端LoginUser结构匹配）
        if (userObj.tokenName && userObj.tokenValue) {
          config.headers[`${userObj.tokenName}`] = userObj.tokenValue
          console.log(`使用tokenName: ${userObj.tokenName} 设置请求头`)
        }
        // 检查其他常见的token字段
        else {
          // 检查是否有token字段
          if (userObj.token) {
            config.headers['token'] = userObj.token
            console.log('使用token字段设置请求头')
          }
          // 检查是否有satoken字段
          else if (userObj.satoken) {
            config.headers['satoken'] = userObj.satoken
            console.log('使用satoken字段设置请求头')
          }
          // 检查是否有accessToken字段
          else if (userObj.accessToken) {
            config.headers['Authorization'] = `Bearer ${userObj.accessToken}`
            console.log('使用accessToken字段设置Authorization请求头')
          }
        }
      } catch (e) {
        console.error('解析登录用户信息失败', e)
      }
    }
    // Do something before request is sent
    return config
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    // 未登录
    if (data.code === 40100) {
      // 不是获取用户信息的请求，并且用户目前不是已经在用户登录页面，则跳转到登录页面
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }
    return response
  },
  function (error) {
    // 处理状态码为400的错误响应
    if (error.response && error.response.status === 400) {
      const { data } = error.response
      // 如果响应中包含message字段，则显示该错误信息
      if (data && data.message) {
        message.error(data.message)
      }
    }
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    return Promise.reject(error)
  },
)

export default myAxios

