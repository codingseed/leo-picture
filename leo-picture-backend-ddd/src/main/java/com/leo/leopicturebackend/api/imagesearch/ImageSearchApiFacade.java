package com.leo.leopicturebackend.api.imagesearch;

import com.leo.leopicturebackend.api.imagesearch.model.ImageSearchResult;
import com.leo.leopicturebackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.leo.leopicturebackend.api.imagesearch.sub.GetImageListApi;
import com.leo.leopicturebackend.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }

    public static void main(String[] args) {
        List<ImageSearchResult> imageList = searchImage("http://p0.so.qhimg.com/t0257d29c212fceba5e.jpg");
        System.out.println("结果列表" + imageList);
    }
}
