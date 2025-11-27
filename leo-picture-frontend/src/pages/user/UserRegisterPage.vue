<template>
  <div id="userRegisterPage">
    <h2 class="title">云梦图坊 - 用户注册</h2>
    <div class="desc">企业级智能协同云图库</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码长度不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请输入确认密码' },
          { min: 8, message: '确认密码长度不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.checkPassword" placeholder="请输入确认密码" />
      </a-form-item>
      <a-form-item name="phone" :rules="[{ required: true, message: '请输入手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式' }]">
        <a-input v-model:value="formState.phone" placeholder="请输入手机号" />
      </a-form-item>
      <a-form-item name="code" :rules="[{ required: true, message: '请输入验证码' }]">
        <a-input-group compact>
          <a-input v-model:value="formState.code" placeholder="请输入验证码" style="width: 60%" />
          <a-button type="link" @click="sendVerificationCode" :disabled="countdown > 0" style="width: 40%">
            {{ countdown > 0 ? `${countdown}秒后重试` : '发送验证码' }}
          </a-button>
        </a-input-group>
      </a-form-item>
      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">注册</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>
<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { userRegisterUsingPost } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { message } from 'ant-design-vue'
import router from '@/router' // 用于接受表单输入的值
import request from '@/request'

// 用于接受表单输入的值
const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
  phone: '',
  code: '',
})

// 验证码倒计时
const countdown = ref(0)
let countdownTimer: number | null = null

const loginUserStore = useLoginUserStore()

/**
 * 发送验证码
 */
const sendVerificationCode = async () => {
  if (!formState.phone) {
    message.error('请先输入手机号')
    return
  }
  
  // 校验手机号格式
  const phoneRegex = /^1[3-9]\d{9}$/
  if (!phoneRegex.test(formState.phone)) {
    message.error('请输入正确的手机号格式')
    return
  }
  
  try {
    // 调用发送验证码接口
    const res = await request('/api/user/sendSms', {
      method: 'POST',
      params: { phone: formState.phone },
    })
    
    if (res.data.code === 0) {
      message.success('验证码发送成功')
      // 开始倒计时
      startCountdown()
    } else {
      message.error('验证码发送失败，' + (res.data.message || '请稍后重试'))
    }
  } catch (error) {
    message.error('验证码发送失败，请稍后重试')
  }
}

/**
 * 开始倒计时
 */
const startCountdown = () => {
  countdown.value = 60
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  countdownTimer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }
  }, 1000)
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  // 校验两次输入的密码是否一致
  if (values.userPassword !== values.checkPassword) {
    message.error('两次输入的密码不一致')
    return
  }
  
  // 校验手机号和验证码
  if (!values.phone || !values.code) {
    message.error('请输入手机号和验证码')
    return
  }
  
  const res = await userRegisterUsingPost(values)
  // 注册成功，跳转到登录页面
  if (res.data.code === 0 && res.data.data) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
  max-width: 360px;
  margin: 0 auto;
}

.title {
  text-align: center;
  margin-bottom: 16px;
}

.desc {
  text-align: center;
  color: #bbb;
  margin-bottom: 16px;
}

.tips {
  color: #bbb;
  text-align: right;
  font-size: 13px;
  margin-bottom: 16px;
}
</style>
