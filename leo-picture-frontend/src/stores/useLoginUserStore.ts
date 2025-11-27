import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUserUsingGet } from '@/api/userController.ts'
import { message } from 'ant-design-vue'

/**
 * 存储登录用户信息的状态
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  // 初始化时从localStorage读取登录信息
  let initialLoginUser: API.LoginUserVO = { userName: '未登录' };
  try {
    const storedLoginUser = localStorage.getItem('loginUser');
    if (storedLoginUser) {
      initialLoginUser = JSON.parse(storedLoginUser);
    }
  } catch (error) {
    console.error('解析localStorage中的登录信息失败:', error);
    localStorage.removeItem('loginUser');
  }
  const loginUser = ref<API.LoginUserVO>(initialLoginUser);

  /**
   * 远程获取登录用户信息
   */
  async function fetchLoginUser() {
    try {
      const res = await getLoginUserUsingGet()
      if (res.data.code === 0 && res.data.data) {
        loginUser.value = res.data.data
        // 存储到localStorage
        localStorage.setItem('loginUser', JSON.stringify(res.data.data))
      } else if (res.data.code === 40100) {
        // 未登录状态，不显示错误消息
        loginUser.value = {
          userName: '未登录',
        }
        // 清除localStorage中的登录信息
        localStorage.removeItem('loginUser')
      } else {
        // 其他错误，不显示错误消息，避免干扰用户体验
        console.log('获取用户信息失败:', res.data.message)
      }
    } catch (error) {
      // 网络错误等情况，也不显示错误消息
      console.error('获取用户信息异常:', error)
    }
  }

  /**
   * 设置登录用户
   * @param newLoginUser
   */
  function setLoginUser(newLoginUser: any) {
    loginUser.value = newLoginUser
    // 存储到localStorage
    if (newLoginUser.userName !== '未登录') {
      localStorage.setItem('loginUser', JSON.stringify(newLoginUser))
    } else {
      localStorage.removeItem('loginUser')
    }
  }

  // 返回
  return { loginUser, fetchLoginUser, setLoginUser }
})
