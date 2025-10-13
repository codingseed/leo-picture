package com.leo.leopicture.domain.user.valueobject;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户角色枚举
 * @author bxl
 * @version 1.0
 * @date 2025-09-22 16:38
 */
@Getter
public enum UserRoleEnum {

    USER("用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;

    private final String value;

    private static final Map<String, UserRoleEnum> userRoleEnumMap = new HashMap<>();
//    static {
//        for (UserRoleEnum userRoleEnum : UserRoleEnum.values())
//            userRoleEnumMap.put(userRoleEnum.getValue(), userRoleEnum);
//    }

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
            if (userRoleEnum.value.equals(value)) {
                return userRoleEnum;
            }
        }
        return null;
//        return userRoleEnumMap.get(value);
    }
}
