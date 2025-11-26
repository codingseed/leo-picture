package com.leo.leopicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 文生图请求
 */
@Data
public class GenerateImageRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 图像描述文本
     */
    private String prompt;
    
    /**
     * 空间ID
     */
    private Long spaceId;
}