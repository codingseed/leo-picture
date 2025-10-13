package com.leo.leopicture.interfaces.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求
 * @author bxl
 * @version 1.0
 * @date 2025-09-22 16:59
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = -8418085190298184154L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;
}
