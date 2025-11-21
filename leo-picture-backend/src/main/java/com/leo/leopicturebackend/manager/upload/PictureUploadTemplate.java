package com.leo.leopicturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.leo.leopicturebackend.config.CosClientConfig;
import com.leo.leopicturebackend.exception.BusinessException;
import com.leo.leopicturebackend.exception.ErrorCode;
import com.leo.leopicturebackend.manager.CosManager;
import com.leo.leopicturebackend.model.dto.file.UploadPictureResult;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author bxl
 * @version 1.0
 * @date 2025-09-25 19:17
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    protected CosManager cosManager;

    @Resource
    protected CosClientConfig cosClientConfig;

    /**
     * 模板方法，定义上传流程
     */
    public final UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 校验图片
        String fileSuffix = validPicture(inputSource);

        // 2. 图片上传地址 文件名：时间戳_uuid.后缀
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginFilename(inputSource);
        String uploadFilename;
        if(!Objects.equals(fileSuffix, "")) {
            uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, fileSuffix);
        } else {
            uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originalFilename));
        }
        //aliyunai扩图上传，会增加?后面的后缀，导致无法上传
        if(uploadFilename.contains("?")) {
            uploadFilename = uploadFilename.split("\\?")[0];
        }
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);

        File file = null;
        try {
            // 3. 创建临时文件
            file = File.createTempFile(uploadPath, null);
            // 处理文件来源（本地或 URL）
            processFile(inputSource, file);
            // 4. 上传图片到对象存储
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 5. 获取图片信息对象，封装返回结果
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 获取到图片处理结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if(CollUtil.isNotEmpty(objectList)) {
                // 获取压缩之后得到的文件信息
                CIObject compressedCiObject = objectList.get(0);
                // 缩略图默认等于压缩图
                CIObject thumbnailCiObject = compressedCiObject;
                // 有生成缩略图，才获取缩略图
                if (objectList.size() > 1) {
                    thumbnailCiObject = objectList.get(1);
                }
                // 封装压缩图的返回结果
                return buildResult(originalFilename, compressedCiObject, thumbnailCiObject, imageInfo);
            }
            // 封装返回结果
            return buildResult(originalFilename, file, uploadPath, imageInfo);
        }  catch (CosClientException e) {
            log.error("云存储客户端异常: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "云存储服务异常: " + e.getMessage());
        } catch (BusinessException e) {
            log.error("业务异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 6. 清理临时文件
            deleteTempFile(file);
        }
    }

    // 抽象方法，子类实现。将不同实现方式的相同方法抽象出来
    /**
     * 校验输入源（本地文件或 URL）
     */
    protected abstract String validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 封装返回结果
     *
     * @param originalFilename   原始文件名
     * @param compressedCiObject 压缩后的对象
     * @param thumbnailCiObject 缩略图对象
     * @param imageInfo 图片信息
     * @return
     */
    private UploadPictureResult buildResult(String originalFilename, CIObject compressedCiObject, CIObject thumbnailCiObject,
                                            ImageInfo imageInfo) {
        if (compressedCiObject == null || thumbnailCiObject == null || imageInfo == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片处理结果不完整");
        }
        // 计算宽高
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        // 设置压缩后的原图地址
        uploadPictureResult.setUrl("https://" + cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());
        // 设置缩略图地址
        uploadPictureResult.setThumbnailUrl("https://" + cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        // 返回可访问的地址
        return uploadPictureResult;
    }

    /**
     * 封装返回结果
     *
     * @param originalFilename
     * @param file
     * @param uploadPath
     * @param imageInfo        对象存储返回的图片信息
     * @return
     */
    private UploadPictureResult buildResult(String originalFilename, File file, String uploadPath, ImageInfo imageInfo) {
        if (imageInfo == null || file == null || StrUtil.isBlank(uploadPath)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片处理参数不完整");
        }
        // 计算宽高
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());
        // 返回可访问的地址
        return uploadPictureResult;
    }

    /**
     * 清理临时文件
     */
    public void deleteTempFile(File tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            if (tempFile.exists()) {
                // 删除临时文件
                boolean delete = tempFile.delete();
                if (!delete) {
                    log.warn("删除临时文件失败,filePath= {}", tempFile.getAbsolutePath());
                } else {
                    log.debug("成功删除临时文件: {}", tempFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.warn("删除临时文件时发生异常: {}", e.getMessage());
        }
    }
}
