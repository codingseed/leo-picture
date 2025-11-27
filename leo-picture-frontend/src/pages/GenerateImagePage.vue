<template>
  <div class="generate-image-page">
    <div class="generate-image-header">
      <h1>文生图</h1>
      <p>通过描述生成您想要的图片</p>
    </div>

    <div class="generate-image-content">
      <!-- 左侧输入区域 -->
      <div class="input-section">
        <div class="form-item">
          <label for="prompt">图片描述</label>
          <textarea
            id="prompt"
            v-model="prompt"
            placeholder="请输入详细的图片描述，描述越详细生成的图片效果越好..."
            rows="6"
            maxlength="500"
          ></textarea>
          <div class="char-count">{{ prompt.length }}/500</div>
        </div>

        <!-- <div class="form-item">
      <label for="spaceId">选择空间</label>
      <a-select
        id="spaceId"
        v-model:value="spaceId"
        placeholder="请选择图片保存的空间"
        style="width: 100%"
      >
        <a-select-option
          v-for="space in spaceList"
          :key="space.id"
          :value="space.id"
        >
          {{ space.name }}
        </a-select-option>
      </a-select>
    </div> -->

        <div class="tips">
          <p><span class="icon">💡</span> 提示：</p>
          <ul>
            <li>次数限制：每人每天5次，每分钟不超过2次</li>
            <li>尽量详细描述您想要的图片内容、风格、构图等</li>
            <li>可以指定艺术风格，如油画、水彩、像素艺术等</li>
            <!-- <li>生成的图片将保存在您选择的空间中</li> -->
          </ul>
        </div>

        <div class="button-group">
          <a-button
            type="primary"
            size="large"
            @click="generateImage"
            :loading="loading"
            :disabled="!prompt || loading"
          >
            {{ loading ? '生成中...' : '生成图片' }}
          </a-button>
          <a-button size="large" @click="clear">清空</a-button>
        </div>
      </div>

      <!-- 右侧预览区域 -->
      <div class="preview-section">
        <div v-if="loading" class="loading-container">
          <a-spin size="large"></a-spin>
          <p>正在生成图片，请稍候...</p>
        </div>

        <div v-else-if="generatedImageUrl" class="image-preview">
          <img :src="generatedImageUrl" alt="生成的图片" />
          <div class="image-actions">
            <a-button size="middle" @click="copyImageUrl" style="margin-right: 8px">
              <copy-outlined /> 复制URL
            </a-button>
            <a-button size="middle" @click="downloadImage">
              <download-outlined /> 下载图片
            </a-button>
            <!-- <a-button size="middle" @click="saveToSpace">
            <save-outlined /> 保存到空间
          </a-button> -->
          </div>
        </div>

        <div v-else class="empty-preview">
          <div class="empty-icon">🎨</div>
          <h3>开始创造您的图片</h3>
          <p class="empty-description">请在左侧输入详细的图片描述，我们将为您生成精美的图像</p>
          <div class="empty-tips">
            <p class="tip-text">💡 小提示：描述越详细，生成的图片效果越好</p>
          </div>
        </div>

        <div v-if="error" class="error-message">
          <a-alert type="error" show-icon :message="error" @close="error = ''" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { DownloadOutlined, SaveOutlined, CopyOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { generateImageByTextUsingPost } from '@/api/pictureController'
// import { listSpaceByPageUsingPost } from '@/api/spaceController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

// 响应式数据
const prompt = ref('')
// const spaceId = ref('')
// const spaceList = ref<any[]>([])
const generatedImageUrl = ref('')
const loading = ref(false)
const error = ref('')
// 移除空状态图片引用，使用emoji图标代替

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 加载用户空间列表
// onMounted(async () => {
//   await loadUserSpaces()
// })

// 加载用户空间
// const loadUserSpaces = async () => {
//   try {
//     // 使用分页查询获取空间列表
//     const response = await listSpaceByPageUsingPost({
//       pageSize: 100, // 获取足够多的空间
//       current: 1
//     })
//     if (response.code === 0 && response.data && response.data.records) {
//       spaceList.value = response.data.records
//       // 默认选择第一个空间
//       if (spaceList.value.length > 0) {
//         spaceId.value = spaceList.value[0].id
//       }
//     }
//   } catch (err) {
//     console.error('加载空间失败:', err)
//     message.error('加载空间失败，请刷新页面重试')
//   }
// }

// 生成图片
const generateImage = async () => {
  if (!prompt.value.trim()) {
    message.warning('请输入图片描述')
    return
  }

  // if (!spaceId.value) {
  //   message.warning('请选择空间')
  //   return
  // }

  loading.value = true
  error.value = ''

  try {
    const response = await generateImageByTextUsingPost({
      prompt: prompt.value.trim(),
    })

    if (response.data && response.data.code === 0) {
      console.log('后端返回的数据:', response.data)
      
      // 检查是否是提示信息（如次数限制、上限等）
      if (typeof response.data.data === 'string' && 
          (response.data.data.includes('次数') || 
           response.data.data.includes('上限') || 
           response.data.data.includes('请明天再试'))) {
        // 使用消息提示组件显示提示信息
        message.info({
          content: response.data.data,
          duration: 5,
          showClose: true
        })
      } 
      // 后端现在直接返回清理后的图片URL
      else if (typeof response.data.data === 'string' && response.data.data.startsWith('http')) {
        // 如果数据是直接的URL字符串
        generatedImageUrl.value = response.data.data
        console.log('直接使用后端返回的URL:', generatedImageUrl.value)
        message.success('图片生成成功')
      } else {
        // 注释掉的后备解析代码...
      }
    } else {
      // 检查是否是限流错误消息
      const responseMessage = response.message || ''
      if (responseMessage.includes('您今天生成图片的次数已达上限')) {
        // 使用消息提示组件显示限流提醒
        message.warning({
          content: responseMessage,
          duration: 5,
          showClose: true
        })
      } else {
        error.value = responseMessage || '图片生成失败'
      }
      console.error('API返回错误:', responseMessage)
    }
  } catch (err) {
    console.error('生成图片失败:', err)
    // 检查是否是限流错误消息
    const errorMessage = err.message || ''
    if (errorMessage.includes('您今天生成图片的次数已达上限')) {
      // 使用消息提示组件显示限流提醒
      message.warning({
        content: errorMessage,
        duration: 5,
        showClose: true
      })
    } else {
      error.value = '图片生成失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

// 复制图片URL
const copyImageUrl = () => {
  if (!generatedImageUrl.value) return

  // 使用现代的剪贴板API
  navigator.clipboard
    .writeText(generatedImageUrl.value)
    .then(() => {
      message.success('URL已复制到剪贴板')
    })
    .catch((err) => {
      console.error('复制失败:', err)
      // 降级方案
      try {
        const textArea = document.createElement('textarea')
        textArea.value = generatedImageUrl.value
        document.body.appendChild(textArea)
        textArea.select()
        document.execCommand('copy')
        document.body.removeChild(textArea)
        message.success('URL已复制到剪贴板')
      } catch (fallbackErr) {
        console.error('降级复制方案也失败:', fallbackErr)
        message.error('复制失败，请手动选择URL')
      }
    })
}

// 下载图片
const downloadImage = () => {
  if (!generatedImageUrl.value) return

  const link = document.createElement('a')
  link.href = generatedImageUrl.value
  link.download = `generated-image-${Date.now()}.jpg`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  message.success('图片下载成功')
}

// 保存到空间
// const saveToSpace = () => {
//   message.success('图片已保存到选定空间')
//   // 这里可以根据实际需求添加额外的保存逻辑
// }

// 清空输入和结果
const clear = () => {
  prompt.value = ''
  generatedImageUrl.value = ''
  error.value = ''
}
</script>

<style scoped>
.generate-image-page {
  padding: 24px;
  background-color: #f5f5f5;
  min-height: 100vh;
}

.generate-image-header {
  text-align: center;
  margin-bottom: 32px;
}

.generate-image-header h1 {
  font-size: 32px;
  margin-bottom: 8px;
  color: #1890ff;
}

.generate-image-header p {
  font-size: 16px;
  color: #666;
}

.generate-image-content {
  display: flex;
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.input-section,
.preview-section {
  flex: 1;
  background: white;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.form-item {
  margin-bottom: 20px;
}

.form-item label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: #333;
}

.form-item textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  resize: vertical;
  transition: border-color 0.3s;
}

.form-item textarea:focus {
  outline: none;
  border-color: #1890ff;
}

.char-count {
  text-align: right;
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.tips {
  background-color: #f0f7ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
  padding: 16px;
  margin-bottom: 24px;
}

.tips p {
  margin: 0 0 8px 0;
  font-weight: 500;
  color: #1890ff;
}

.tips ul {
  margin: 0;
  padding-left: 20px;
}

.tips li {
  margin-bottom: 4px;
  color: #666;
}

.button-group {
  display: flex;
  gap: 12px;
}

.button-group button {
  flex: 1;
}

.preview-section {
  display: flex;
  flex-direction: column;
}

.loading-container,
.empty-preview {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  color: #999;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
  animation: float 3s ease-in-out infinite;
}

.empty-preview h3 {
  font-size: 20px;
  margin-bottom: 8px;
  color: #333;
  font-weight: 500;
}

.empty-description {
  font-size: 14px;
  color: #666;
  margin-bottom: 16px;
  text-align: center;
  max-width: 300px;
  line-height: 1.5;
}

.empty-tips {
  background-color: #f0f7ff;
  border-radius: 8px;
  padding: 12px 16px;
  margin-top: 16px;
}

.tip-text {
  margin: 0;
  font-size: 12px;
  color: #1890ff;
  line-height: 1.4;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.image-preview {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.image-preview img {
  max-width: 100%;
  max-height: 500px;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.image-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}

.error-message {
  margin-top: 20px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .generate-image-content {
    flex-direction: column;
  }

  .generate-image-header h1 {
    font-size: 24px;
  }
}
</style>
