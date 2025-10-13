package com.leo.leopicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.leopicture.domain.user.entity.User;
import com.leo.leopicture.domain.user.repository.UserRepository;
import com.leo.leopicture.infrastructure.mapper.UserMapper;
import org.springframework.stereotype.Service;


/**
 * 用户仓储实现
 */
@Service
public class UserRepositoryImpl extends ServiceImpl<UserMapper, User> implements UserRepository {
}
