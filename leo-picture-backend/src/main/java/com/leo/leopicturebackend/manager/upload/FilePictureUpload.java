package com.leo.leopicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.leo.leopicturebackend.exception.BusinessException;
import com.leo.leopicturebackend.exception.ErrorCode;
import com.leo.leopicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author bxl
 * @version 1.0
 * @date 2025-09-25 19:21
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate {

    // 定义常量，提高可读性和维护性
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
    private static final List<String> ALLOWED_SUFFIXES = Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "tiff"
    );

    @Override
    protected String validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1. 校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
        // 2. 校验文件后缀
//        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        String fileSuffix = getSuffixFromContentType(Objects.requireNonNull(multipartFile.getContentType()));
        // 允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
        return fileSuffix;
    }

    private String getSuffixFromContentType(String contentType) {

        switch(contentType) {
            case "image/jpeg":
                return "jpeg";
            case "image/png":
                return "png";
            case "image/webp":
                return "webp";
            case "image/jpg":
                return "jpg";
            default:
                return "";
        }
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        // 添加异常处理，提供更具体的错误信息
        try {
            multipartFile.transferTo(file);
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件状态异常，上传失败");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件传输失败: " + e.getMessage());
        }
    }
}
