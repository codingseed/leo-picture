package com.leo.leopicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leo.leopicturebackend.model.entity.AiImageUsage;

import java.time.LocalDate;

public interface AiUsageService extends IService<AiImageUsage> {

    /**
     * 检查用户今日是否还可生成图片
     */
    boolean canGenerateImageToday(Long userId);

    /**
     * 获取用户今日剩余次数
     */
    int getRemainingCountToday(Long userId);

    /**
     * 增加用户今日使用次数
     * @param userId
     */
    void incrementUsage(Long userId);

    /**
     * 获取用户今日使用次数
     * @param userId
     * @param today
     * @return
     */
    AiImageUsage getTodayUsage(Long userId, LocalDate today);


}
