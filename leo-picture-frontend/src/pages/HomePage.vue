<template></template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
// import {
//   listPictureTagCategoryUsingGet,
//   listPictureVoByPageUsingPost,
// } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
// import PictureList from '@/components/PictureList.vue' // 定义数据

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// // 获取数据
// const fetchData = async () => {
//   loading.value = true
//   // 转换搜索参数
//   const params = {
//     ...searchParams,
//     tags: [] as string[],
//   }
//   if (selectedCategory.value !== 'all') {
//     params.category = selectedCategory.value
//   }
//   // [true, false, false] => ['java']
//   selectedTagList.value.forEach((useTag, index) => {
//     if (useTag) {
//       params.tags.push(tagList.value[index])
//     }
//   })
//   const res = await listPictureVoByPageUsingPost(params)
//   if (res.data.code === 0 && res.data.data) {
//     dataList.value = res.data.data.records ?? []
//     total.value = res.data.data.total ?? 0
//   } else {
//     message.error('获取数据失败，' + res.data.message)
//   }
//   loading.value = false
// }

// // 页面加载时获取数据，请求一次
// onMounted(() => {
//   fetchData()
// })

// // 分页参数
// const onPageChange = (page: number, pageSize: number) => {
//   searchParams.current = page
//   searchParams.pageSize = pageSize
//   fetchData()
// }

// // 搜索
// const doSearch = () => {
//   // 重置搜索条件
//   searchParams.current = 1
//   fetchData()
// }

// 标签和分类列表
const categoryList = ref<string[]>([])
const selectedCategory = ref<string>('all')
const tagList = ref<string[]>([])
const selectedTagList = ref<boolean[]>([])

// /**
//  * 获取标签和分类选项
//  * @param values
//  */
// const getTagCategoryOptions = async () => {
//   const res = await listPictureTagCategoryUsingGet()
//   if (res.data.code === 0 && res.data.data) {
//     tagList.value = res.data.data.tagList ?? []
//     categoryList.value = res.data.data.categoryList ?? []
//   } else {
//     message.error('获取标签分类列表失败，' + res.data.message)
//   }
// }
//
// onMounted(() => {
//   getTagCategoryOptions()
// })
</script>

<style scoped>
#homePage {
  margin-bottom: 16px;
}

#homePage .search-bar {
  max-width: 480px;
  margin: 0 auto 16px;
}

#homePage .tag-bar {
  margin-bottom: 16px;
}
</style>
