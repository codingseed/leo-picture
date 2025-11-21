package com.leo.leopicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.leopicturebackend.model.entity.AiImageUsage;

public interface AiUsageMapper extends BaseMapper<AiImageUsage> {
    // 继承BaseMapper已经包含基本的CRUD方法
    // 可以添加自定义查询方法
}
