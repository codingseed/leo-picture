package com.leo.leopicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.leopicture.domain.picture.entity.Picture;
import com.leo.leopicture.domain.picture.repository.PictureRepository;
import com.leo.leopicture.infrastructure.mapper.PictureMapper;
import org.springframework.stereotype.Service;

/**
 * 图片仓储实现
 */
@Service
public class PictureRepositoryImpl extends ServiceImpl<PictureMapper, Picture> implements PictureRepository {
}