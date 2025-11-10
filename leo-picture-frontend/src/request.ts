import axios from 'axios'
import { message } from 'ant-design-vue'

// 区分开发和生产环境
const DEV_BASE_URL = 'http://localhost:8123'
const PROD_BASE_URL = 'http://pic.codingseed.site'
// 创建 Axios 实例
const myAxios = axios.create({
  baseURL: DEV_BASE_URL,
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
    let loginUser: LoginUser = {} // 明确指定类型

    // 解析存储的用户信息（防止解析失败）
    if (loginUserJson) {
      try {
        loginUser = JSON.parse(loginUserJson) as LoginUser
      } catch (e) {
        console.error('解析登录用户信息失败', e)
      }
    }
    // 请求头添加token
    if (loginUser.tokenName && loginUser.tokenValue) {
      config.headers[`${loginUser.tokenName}`] = loginUser.tokenValue
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
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    return Promise.reject(error)
  },
)

export default myAxios
