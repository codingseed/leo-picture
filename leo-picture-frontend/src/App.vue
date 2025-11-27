<template>
  <div id="app">
    <a-config-provider :locale="zhCN">
      <BasicLayout />
    </a-config-provider>
  </div>
</template>

<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
dayjs.locale('zh-cn')

import BasicLayout from './layouts/BasicLayout.vue'
// import { healthUsingGet } from '@/api/mainController.ts'
// import {useLoginUserStore} from "@/stores/useLoginUserStore.ts";

// 已经改为在权限校验文件中获取
// const loginUserStore = useLoginUserStore()
// loginUserStore.fetchLoginUser()

// healthUsingGet().then((res) => {
//   console.log(res)
// })
</script>

<style>
/* 全局样式 - 隐藏所有滚动条 */
body, html {
  overflow: hidden;
  margin: 0;
  padding: 0;
}

/* 隐藏滚动条但保留滚动功能 */
::-webkit-scrollbar {
  display: none;
}

/* Firefox 支持 */
* {
  scrollbar-width: none;
}

/* IE 和 Edge 支持 */
* {
  -ms-overflow-style: none;
}
</style>

<style scoped></style>
