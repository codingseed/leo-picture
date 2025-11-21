package com.leo.leopicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.leopicturebackend.mapper.AiUsageMapper;
import com.leo.leopicturebackend.model.entity.AiImageUsage;
import com.leo.leopicturebackend.service.AiUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
public class AiUsageServiceImpl extends ServiceImpl<AiUsageMapper,AiImageUsage> implements AiUsageService {

    @Autowired
    private AiUsageMapper aiUsageMapper;
    @Override
    public boolean canGenerateImageToday(Long userId) {
        LocalDate today = LocalDate.now();
        AiImageUsage todayUsage = getTodayUsage(userId, today);

        if (todayUsage == null) {
            return true; // 今天第一次使用，可以生成
        }

        return todayUsage.getUsageCount() < todayUsage.getMaxDailyLimit();
    }

    @Override
    public int getRemainingCountToday(Long userId) {
        LocalDate today = LocalDate.now();
        AiImageUsage todayUsage = getTodayUsage(userId, today);

        if (todayUsage == null) {
            return 5; // 默认限制5次
        }

        return Math.max(0, todayUsage.getMaxDailyLimit() - todayUsage.getUsageCount());
    }

    @Override
    public void incrementUsage(Long userId) {
        LocalDate today = LocalDate.now();
        AiImageUsage todayUsage = getTodayUsage(userId, today);

        if (todayUsage == null) {
            // 创建今天的记录
            todayUsage = new AiImageUsage();

            todayUsage.setUserId(userId);
            todayUsage.setUsageDate(today);
            todayUsage.setUsageCount(1);
            todayUsage.setMaxDailyLimit(5);
            todayUsage.setCreatedTime(LocalDateTime.now());
            todayUsage.setUpdatedTime(LocalDateTime.now());
            aiUsageMapper.insert(todayUsage);
            log.info("用户 {} 今日首次使用AI图片生成", userId);
        } else {
            // 更新次数
            todayUsage.setUsageCount(todayUsage.getUsageCount() + 1);
            todayUsage.setUpdatedTime(LocalDateTime.now());
            aiUsageMapper.updateById(todayUsage);
            log.info("用户 {} 今日第 {} 次使用AI图片生成", userId, todayUsage.getUsageCount());
        }
    }

    @Override
    public AiImageUsage getTodayUsage(Long userId, LocalDate today) {
        QueryWrapper<AiImageUsage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId)
                .eq("usage_date", today);
        return aiUsageMapper.selectOne(queryWrapper);
    }
}
