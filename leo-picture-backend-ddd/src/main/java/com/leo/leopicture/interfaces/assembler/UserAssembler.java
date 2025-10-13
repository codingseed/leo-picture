package com.leo.leopicture.interfaces.assembler;

import com.leo.leopicture.domain.user.entity.User;
import com.leo.leopicture.interfaces.dto.user.UserAddRequest;
import com.leo.leopicture.interfaces.dto.user.UserUpdateRequest;
import org.springframework.beans.BeanUtils;

/**
 * 用户对象转换
 */
public class UserAssembler {

    public static User toUserEntity(UserAddRequest request) {
        User user = new User();
        BeanUtils.copyProperties(request, user);
        return user;
    }

    public static User toUserEntity(UserUpdateRequest request) {
        User user = new User();
        BeanUtils.copyProperties(request, user);
        return user;
    }
}