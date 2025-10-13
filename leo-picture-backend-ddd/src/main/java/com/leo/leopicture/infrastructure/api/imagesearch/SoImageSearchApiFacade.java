package com.leo.leopicture.infrastructure.api.imagesearch;

import com.leo.leopicture.infrastructure.api.imagesearch.model.SoImageSearchResult;
import com.leo.leopicture.infrastructure.api.imagesearch.sub.GetSoImageListApi;
import com.leo.leopicture.infrastructure.api.imagesearch.sub.GetSoImageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 360搜图图片搜索接口
 * <p>
 * 这里用了 门面模式
 * @author bxl
 * @version 1.0
 * @date 2025-10-03 16:59
 */
@Slf4j
public class SoImageSearchApiFacade {

    /**
     * 搜索图片
     *
     * @param imageUrl 需要以图搜图的图片地址
     * @param start    开始下表
     * @return 图片搜索结果列表
     */
    public static List<SoImageSearchResult> searchImage(String imageUrl, Integer start) {
        String soImageUrl = GetSoImageUrlApi.getSoImageUrl(imageUrl);
        List<SoImageSearchResult> imageList = GetSoImageListApi.getImageList(soImageUrl, start);
        return imageList;
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "http://p0.so.qhimg.com/t0257d29c212fceba5e.jpg";
        List<SoImageSearchResult> resultList = searchImage(imageUrl, 0);
        System.out.println("结果列表" + resultList);
    }
}
