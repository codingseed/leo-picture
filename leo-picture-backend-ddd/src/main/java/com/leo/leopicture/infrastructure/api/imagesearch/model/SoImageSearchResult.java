package com.leo.leopicture.infrastructure.api.imagesearch.model;

/**
 * @author bxl
 * @version 1.0
 * @date 2025-10-03 16:56
 */

import lombok.Data;

/**
 * 360搜图图片搜索结果
 */
@Data
public class SoImageSearchResult {

    /**
     * 图片地址
     */
    private String imgUrl;

    /**
     * 标题
     */
    private String title;

    /**
     * 图片key
     */
    private String imgkey;

    /**
     * HTTP
     */
    private String http;

    /**
     * HTTPS
     */
    private String https;
}
