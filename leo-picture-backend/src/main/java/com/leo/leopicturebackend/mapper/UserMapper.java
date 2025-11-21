package com.leo.leopicturebackend.mapper;

import com.leo.leopicturebackend.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
* @author Leo
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2025-09-22 16:22:55
* @Entity com.leo.leopicturebackend.model.entity.User
*/
public interface UserMapper extends BaseMapper<User> {
    String selectPhone(@Param("phone") String phone);

}




