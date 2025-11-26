package com.leo.leopicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.leopicturebackend.api.aliyunai.AliYunAiApi;
import com.leo.leopicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.leo.leopicturebackend.ai.tools.ImageGenerationTool;
import com.leo.leopicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.leo.leopicturebackend.config.RabbitConfig;
import com.leo.leopicturebackend.exception.BusinessException;
import com.leo.leopicturebackend.exception.ErrorCode;
import com.leo.leopicturebackend.exception.ThrowUtils;
import com.leo.leopicturebackend.manager.CosManager;
import com.leo.leopicturebackend.manager.RateLimiterManager;
import com.leo.leopicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.leo.leopicturebackend.manager.upload.FilePictureUpload;
import com.leo.leopicturebackend.manager.upload.PictureUploadTemplate;
import com.leo.leopicturebackend.manager.upload.UrlPictureUpload;
import com.leo.leopicturebackend.mapper.PictureMapper;
import com.leo.leopicturebackend.model.dto.file.UploadPictureResult;
import com.leo.leopicturebackend.model.dto.picture.*;
import com.leo.leopicturebackend.model.entity.Picture;
import com.leo.leopicturebackend.model.entity.Space;
import com.leo.leopicturebackend.model.entity.User;
import com.leo.leopicturebackend.model.enums.PictureReviewStatusEnum;
import com.leo.leopicturebackend.model.enums.SpaceTypeEnum;
import com.leo.leopicturebackend.model.vo.PictureVO;
import com.leo.leopicturebackend.model.vo.UserVO;
import com.leo.leopicturebackend.service.PictureService;
import com.leo.leopicturebackend.service.SpaceService;
import com.leo.leopicturebackend.service.UserService;
import com.leo.leopicturebackend.utils.ColorSimilarUtils;
import com.leo.leopicturebackend.utils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author Leo
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2024-12-11 20:45:51
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

//    @Resource
//    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Autowired
    private CosManager cosManager;

    @Resource
    private TransactionTemplate transactionTemplate;

//    @Resource
//    private ThreadPoolExecutor customExecutor;

    @Resource
    private AliYunAiApi aliYunAiApi;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RateLimiterManager rateLimiterManager;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource(name = "pictureUploadExecutor")
    private ThreadPoolExecutor executorService;
    @Resource
    private ImageGenerationTool imageGenerationTool;

    // 最大图像大小：10MB（字节）
    private static final long MAX_SIZE = 10 * 1024 * 1024;
    // 图像维度（宽/高）的最小、最大值（像素）
    private static final int MIN_DIMENSION = 512;
    private static final int MAX_DIMENSION = 4096;


    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        // 如果传递了 url，才校验
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    public PictureVO uploadPicture(Object inputSource,
                                   PictureUploadRequest pictureUploadRequest, User loginUser) throws InterruptedException {
        if (inputSource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片为空");
        }
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        //初始化一些变量
        Long spaceId = pictureUploadRequest.getSpaceId();
        Long pictureId = pictureUploadRequest.getId();
        Picture oldPicture = null;
        RLock lock = null;
        boolean locked = false;

        // 1. 空间相关处理校验空间是否存在
        Space space = null;
        if (spaceId != null) {
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
//            // 校验是否有空间的权限，仅空间管理员才能上传，已废除，部分团队成员也有权限，改为使用SaToken統一权限校验
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue()){
                // 仅当团队空间存在时获取分布式锁，避免在团队空间图片上传时多个线程同时上传图片
                //空间唯一ID加锁，确保同一空间内的操作串行化，而不同空间之间可以并行操作
                String lockKey = "spaceLock:" + spaceId;
                lock = redissonClient.getLock(lockKey);// Java 内存中创建一个 RLock 对象实例.绑定到指定的 lockKey
                // 尝试获取锁,重试等待时间1s（不设置超时时间，触发看门狗机制）。TTL默认30秒表示：如果从现在开始不再续期，这个锁会在30秒后自动过期（保证客户端崩溃不会死锁）。
//                locked = lock.tryLock(2, 5, TimeUnit.SECONDS);
                locked = lock.tryLock(1, TimeUnit.SECONDS);
                if (!locked) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "系统繁忙，请稍后再试");
                }
                //如果获取锁成功，重新加载space，因为获取锁期间可能被其他线程修改
                space = spaceService.getById(spaceId);
            }
            //校验额度锁保护
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        // 2.如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 改为使用统一的权限校验
//            // 仅本人或管理员可编辑
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId（这样也兼容了公共图库）
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原图片的空间 id 一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        // 3.上传图片，得到图片信息
        // 按照用户 id 划分目录 => 按照空间划分目录,上传路径处理
        String uploadPathPrefix = spaceId == null ?
                String.format("public/%s", loginUser.getId()):
                String.format("space/%s", spaceId);
        // 4.根据 inputSource 的类型区分上传方式（多态），默认是本地图片上传
        PictureUploadTemplate pictureUploadTemplate = (inputSource instanceof String) ?
                urlPictureUpload : filePictureUpload;

        // 5. 执行上传操作（在锁保护下）
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 6.构造要入库的图片信息
        Picture picture = buildPictureEntity(pictureUploadRequest,uploadPictureResult, loginUser, spaceId,pictureId);
        try {

            // 7.开启事务
            Long finalSpaceId = spaceId;
            Space finalSpace = space;
            transactionTemplate.execute(status -> {
                // DB插入数据
                boolean result = this.saveOrUpdate(picture);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
                if (finalSpaceId != null) {
                    // 更新空间的使用额度(带并发安全控制)
                    boolean updateResult = spaceService.lambdaUpdate()
                            .eq(Space::getId, finalSpaceId)
                            //小于最大限制才允许更新
                            .lt(Space::getTotalCount, finalSpace.getMaxCount())
                            .lt(Space::getTotalSize, finalSpace.getMaxSize())
                            .setSql("totalSize = totalSize + " + picture.getPicSize())
                            .setSql("totalCount = totalCount + 1")
                            .update();
                    //双重验证,检查是否超额
                    if (!updateResult || spaceService.getById(finalSpaceId).getTotalSize() >= finalSpace.getMaxSize()){
                        throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间额度不足");
                    }
                }
                return picture;
            });

            // 如果是更新，可以清理旧图片资源
            if (pictureId != null) {
                // 异步清理COS文件，而非DB记录！（额度归还会稍微不够准确）
                this.clearPictureFile(oldPicture);
//            this.deletePicture(oldPicture.getId(), loginUser);
            }
            return PictureVO.objToVo(picture);
        }catch (Exception e){
            // 8. 事务失败处理,清除上传的COS文件
            try {
                clearPictureFiles(picture.getUrl(), picture.getThumbnailUrl());
                log.info("图片上传事务失败，已清理COS文件: {}", uploadPictureResult.getUrl());
            }catch (Exception cleanException){
                log.error("清理COS文件失败: {}", cleanException.getMessage());
            }
            //抛出原始异常
            throw e;
        }finally {
            //释放缓存
            releaseLock(locked, lock);
            this.clearPageCache();
        }
    }

    private static void releaseLock(boolean locked, RLock lock) {
        if ( locked && lock !=null){
            try {
                if (lock.isLocked()&& lock.isHeldByCurrentThread()){
                    lock.unlock();
                }
            }catch (Exception e){
                log.error("解锁失败: {}", e.getMessage());
            }
        }
    }
    // 对上传数据库失败的图片清理cos方法
    private void clearPictureFiles(String url, String thumbnailUrl) {
        try {
            // 检查是否有其他引用
            long refCount = this.lambdaQuery()
                    .eq(Picture::getUrl, url)
                    .count();
            if (refCount == 0) {
                cosManager.deleteObject(url);
                if (StrUtil.isNotBlank(thumbnailUrl)) {
                    cosManager.deleteObject(thumbnailUrl);
                }
            }
        } catch (Exception e) {
            log.error("文件清理异常", e);
        }
    }
    private Picture buildPictureEntity(PictureUploadRequest pictureUploadRequest,UploadPictureResult uploadPictureResult,
            User loginUser, Long spaceId,Long pictureId) {
        Picture picture = new Picture();
        picture.setSpaceId(spaceId);//指定空间ID
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setPicColor(ColorTransformUtils.normalizeHexColor(uploadPictureResult.getPicColor()));
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        //支持外层传递图片名称
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        //填充审核参数
        fillReviewParams(picture, loginUser);
        //pictureId不为空则是更新,校验图片存在,为空就是新增图片
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        return picture;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 1,2,3,4 id是乱序的
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                List<User> userList = userIdUserListMap.get(userId);
                if (userList != null && !userList.isEmpty()) {
                    user = userList.get(0);
                }
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // and (name like "%xxx%" or introduction like "%xxx%")
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // >= startEditTime
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // < endEditTime
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            /* and (tag like "%\"Java\"%" and like "%\"Python\"%") */
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        //String reviewMessage = pictureReviewRequest.getReviewMessage();
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 校验审核状态是否重复，已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 4. 数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，无论是编辑还是创建默认都是待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 批量抓取图片请求
     * @param loginUser                   登录用户
     * @return 成功创建的图片数
     */
    // 批量上传需要处理多张图片，每张图片的上传都是独立的I/O操作
    // 如果串行执行，用户需要等待所有图片上传完成才能得到响应
    // 所以这里使用线程池并发执行多个图片上传任务，并使用CompletableFuture异步执行，并且异常隔离
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        //校验参数
        Integer count = pictureUploadByBatchRequest.getCount();
        String searchText = pictureUploadByBatchRequest.getSearchText();
        //默认名称前缀
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多30条");
        //抓取内容,%s占位searchText
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        // 使用 Jsoup 抓取网页并解析为 Document 对象
        Document document;
        try {
            document = Jsoup.connect(fetchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .referrer("https://cn.bing.com")
                    .timeout(10000) // 增加超时时间
                    .maxBodySize(0) // 不限制响应体大小
                    .get();
        } catch (IOException e) {
            log.error("图片抓取失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片抓取失败");
        }

        //解析内容，从整个文档中找到具有 dgControl 类的第一个元素
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片解析获取元素失败");
        }
        // 从该 div 元素中选择所有具有 iusc 类的子元素
        Elements imgElementList = div.select(".iusc");

        // 使用 CompletableFuture 并发处理图片上传
        List<CompletableFuture<Boolean>> uploadFutures = new ArrayList<>();

//        // 创建一个专用的线程池用于执行异步任务，避免与其他任务竞争
//        ThreadPoolExecutor batchUploadExecutor = new ThreadPoolExecutor(
//                10, 20, 5,
//                TimeUnit.SECONDS, new LinkedBlockingQueue<>(200),
//                r -> {
//                    Thread thread = new Thread(r, "batch-upload-thread-");
//                    thread.setDaemon(false);
//                    return thread;
//                },
//                new ThreadPoolExecutor.CallerRunsPolicy());

        //取一个最大值，count默认10
        int maxUploads = Math.min(count, imgElementList.size());

        // 添加简单监控，单例定时线程池，独立于主线程运行
        ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor();
        // 固定频率执行定时任务。(Runnable command(要执行的任务（Runnable), long initialDelay(首次执行的延迟时间), long period连续执行之间的时间间隔（周期), TimeUnit unit)
        monitorExecutor.scheduleAtFixedRate(() -> {
                    int queueSize = executorService.getQueue().size();
                    int activeCount = executorService.getActiveCount();
                    long completedTaskCount = executorService.getCompletedTaskCount();
                    if (queueSize > 100) {
                        log.warn("图片上传队列堆积 | 队列大小: {}, 活跃线程数: {}, 已完成任务数: {}", queueSize, activeCount, completedTaskCount);
                    } else {
                        log.info("图片上传状态 | 队列大小: {}, 活跃线程数: {}, 已完成任务数: {}", queueSize, activeCount, completedTaskCount);
                    }
                },
                5, 5, TimeUnit.SECONDS);

        //遍历图片处理
        for (int i = 0; i < maxUploads; i++) {
            Element element = imgElementList.get(i);
            // 获取data-m属性中的JSON字符串
            String dataM = element.attr("m");
            String fileUrl;
            try {
                JSONObject jsonObject = JSONUtil.parseObj(dataM);
                fileUrl = jsonObject.getStr("murl");
            } catch (Exception e) {
                log.error("图片解析失败：{}", e.getMessage());
                continue;
            }
            if (StrUtil.isBlank(fileUrl)) {
                log.info("图片地址连接为空，已跳过：{}", fileUrl);
                continue;
            }
            //处理图片地址，防止转义或者和对象存储冲突
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            //准备上传参数
            final String finalFileUrl = fileUrl;
            final int index = i;
            String finalNamePrefix = namePrefix;
            // 使用 CompletableFuture 异步处理图片上传
            CompletableFuture<Boolean> uploadFuture = CompletableFuture.supplyAsync(() -> {
                PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
                pictureUploadRequest.setFileUrl(finalFileUrl);
                pictureUploadRequest.setPicName(finalNamePrefix + (index + 1));

                // 添加稍后重试机制
                int maxRetries = 3;
                for (int retry = 0; retry < maxRetries; retry++) {
                    try {
                        PictureVO pictureVO = this.uploadPicture(finalFileUrl, pictureUploadRequest, loginUser);
                        log.info("图片上传成功：id= {}, url={}, 重试次数={}", pictureVO.getId(), finalFileUrl, retry);
                        return true;
                    } catch (Exception e) {
                        log.warn("图片上传失败，正在进行第{}次重试：{}, url={}", retry + 1, e.getMessage(), finalFileUrl);
                        if (retry == maxRetries - 1) {
                            // 最后一次重试仍然失败
                            log.error("图片上传最终失败：{}, url={}", e.getMessage(), finalFileUrl);
                            return false;
                        }
                        // 等待一段时间再重试
                        try {
                            Thread.sleep(1000 * (retry + 1)); // 递增等待时间
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                }
                return false;
            }, executorService).exceptionally(          // 可对单个任务处理异常
                    throwable -> {
                        // 增强的异常处理
                        if (throwable.getCause() instanceof RejectedExecutionException) {
                            log.warn("线程池拒绝执行任务，采用降级策略: {}", finalFileUrl);
                            // 降级方案: 同步执行
                            try {
                                PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
                                pictureUploadRequest.setFileUrl(finalFileUrl);
                                pictureUploadRequest.setPicName(finalNamePrefix + (index + 1));
                                PictureVO pictureVO = this.uploadPicture(finalFileUrl, pictureUploadRequest, loginUser);
                                log.info("降级处理成功：id= {}, url={}", pictureVO.getId(), finalFileUrl);
                                return true;
                            } catch (Exception e) {
                                log.error("降级处理失败：{},url={}", e.getMessage(), finalFileUrl);
                                return false;
                            }
                        }
                log.error("第{}张图片上传失败：{},url={}", index,throwable.getMessage(),finalFileUrl);
                return false;
            });
            uploadFutures.add(uploadFuture);
        }
        // 等待所有上传任务完成并统计成功数量
        int uploadCount = 0;

        /*开始
│
├─ 提交uploadFutures到线程池（并发执行）
│
├─ 创建allOf Future（等待所有任务完成）
│
├─ 添加thenApply回调（但暂不执行）
│
├─ 阻塞等待(最多60秒)
│   │
│   ├─ 所有任务完成 → 执行thenApply回调 → 收集结果 → get()返回
│   │
│    └─ 超时 → 抛出TimeoutException
│
└─ 统计结果 → 关闭线程池 → 返回成功计数
*/
        try {
            //allof静态方法。等待所用任务执行完后获取结果
            List<Boolean> results = CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0]))
                    //allOf完成后触发执行结果收集
                    .thenApply(v -> uploadFutures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()))
                    .get(120, TimeUnit.SECONDS); // 增加超时时间到120秒
            uploadCount = (int) results.stream().filter(Boolean::booleanValue).count();
        } catch (Exception e) {
            log.error("批量上传过程中发生异常", e);
        } finally {
            // 关闭线程池
            executorService.shutdown();
            try {
                // 等待线程池终止最多30秒
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }

            // 关闭监控线程池
            monitorExecutor.shutdown();
            try {
                if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitorExecutor.shutdownNow();
            }
        }
        return uploadCount;
    }

/*    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 名称前缀默认等于搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }

        // 获取class['dgcontrol’]元素下的所有img[class='mimg’]元素
        // 这里获得的是已经处理过的图片而非原图
//         Elements imgElementList = div.select("img.mimg");

        // 原图存放在a[class="iusc"]标签下的m属性中的murl中
        Elements imgElementList = div.select("a.iusc");
        // 遍历元素，依次处理上传图片
        int uploadCount = 0;

        for (Element imgElement : imgElementList) {
//             String fileUrl = imgElement.attr("src");


//            // 获取m属性
//            String m_attr = imgElement.attr("m");
//            // 将m属性字符串转为map对象
//            HashMap<String,String> mMap = JSONUtil.toBean(m_attr, HashMap.class);
//            String fileUrl = mMap.get("murl");

            // 获取data-m属性中的JSON字符串
            String dataM = imgElement.attr("m");
            String fileUrl;
            try {
                // 解析JSON字符串
                JSONObject jsonObject = JSONUtil.parseObj(dataM);
                // 获取murl字段（原始图片URL）
                fileUrl = jsonObject.getStr("murl");
            } catch (Exception e) {
                log.error("解析图片数据失败", e);
                continue;
            }

            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片的地址，防止转义或者和对象存储冲突的问题
            // baidu.com?leo=dog，应该只保留 baidu.com
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }*/

    /**
     * 清理图片文件
     *
     * @param oldPicture
     */
    @Async//异步执行,直接返回给前端记录，后台慢慢执行方法,事务执行最好不要异步
    protected void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用（实现了秒传）
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // 删除图片
        cosManager.deleteObject(pictureUrl);
        // 删除缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限，已经改为使用注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的使用额度，释放额度
            if (oldPicture.getSpaceId() != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, oldPicture.getSpaceId())
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        Map<String, Object> message = new HashMap<>();
        message.put("pictureId", pictureId);
        message.put("operation", "delete");
        //convert将 Java 对象自动转换为 AMQP 消息,对象转换
        //消息属性设置messagePostProcessor
        rabbitTemplate.convertAndSend(RabbitConfig.PICTURE_UPDATE_EXCHANGE,
                RabbitConfig.PICTURE_UPDATE_ROUTING_KEY,message
/*                ,messagePostProcessor -> {
                    // 确保消息持久化
                    messagePostProcessor.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    // 设置消息ID便于追踪
                    messagePostProcessor.getMessageProperties().setMessageId(UUID.randomUUID().toString());
                    return messagePostProcessor;
                }*/
        );

        // 上传完成后清理redis缓存
        this.clearPageCache();
        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限，已经改为使用注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        Map<String, Object> message = new HashMap<>();
        message.put("pictureId", picture.getId());
        message.put("operation", "update");
        //convert将 Java 对象自动转换为 AMQP 消息,对象转换
        //消息属性设置messagePostProcessor
        rabbitTemplate.convertAndSend(RabbitConfig.PICTURE_REVIEW_EXCHANGE,
                RabbitConfig.PICTURE_REVIEW_ROUTING_KEY, message
/*                ,messagePostProcessor -> {
                    // 确保消息持久化
                    messagePostProcessor.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    // 设置消息ID便于追踪
                    messagePostProcessor.getMessageProperties().setMessageId(UUID.randomUUID().toString());
                    return messagePostProcessor;
                }*/
        );
        // 更新完成后清理redis缓存
        this.clearPageCache();
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下的所有图片（必须要有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        // 将颜色字符串转换为主色调
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictureList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片会默认排序到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 计算相似度
                    // 越大越相似。取反升序
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
//                // 过滤掉相似度低于50%的图片
//                .filter(picture -> ColorSimilarUtils.calculateSimilarity(targetColor, Color.decode(picture.getPicColor())) > 0.5)
                .limit(12) // 取前 12 个
                .collect(Collectors.toList());
        // 5. 返回结果
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Override
//    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "指定的图片不存在或不属于该空间");
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        // 5. 操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
        // 批量更新完成后清理缓存
        this.clearPageCache();

//        // 分批处理避免长事务
//        int batchSize = 100;
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//        for (int i = 0; i < pictureList.size(); i += batchSize) {
//            List<Picture> batch = pictureList.subList(i, Math.min(i + batchSize, pictureList.size()));
//
//            // 异步处理每批数据
//            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                batch.forEach(picture -> {
//                    // 编辑分类和标签
//                    if (request.getCategory() != null) {
//                        picture.setCategory(request.getCategory());
//                    }
//                    if (request.getTags() != null) {
//                        picture.setTags(String.join(",", request.getTags()));
//                    }
//                });
//                boolean result = this.updateBatchById(batch);
//                if (!result) {
//                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量更新图片失败");
//                }
//            }, customExecutor);
//
//            futures.add(future);
//        }
//
//        // 等待所有任务完成
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        boolean tryAcquire = rateLimiterManager.tryAcquire(1, TimeUnit.SECONDS);
        ThrowUtils.throwIf(!tryAcquire, ErrorCode.PARAMS_ERROR,"请求过于频繁请稍后重试");
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        //Optional 主要用于优雅地处理可能为 null 的对象，避免空指针异常（NullPointerException）
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 校验权限，已经改为使用注解鉴权
//        checkPictureAuth(loginUser, picture);
        // 校验待扩图片是否符合限制条件
        checkPictureOutPainting(picture);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();

        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());

        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }

    private void checkPictureOutPainting(Picture picture) {
        // 校验图像大小
        if (picture.getPicSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, String.format("图像大小超过10MB限制，当前大小：%.2fMB",
                    picture.getPicSize() / (1024.0 * 1024)));
        }
//        // 校验图像分辨率与单边长度
//        int width = picture.getPicWidth();
//        int height = picture.getPicHeight();
//
//        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, String.format("图像分辨率过低，宽/高小于512像素，当前宽：%d，高：%d",
//                    width, height));
//        }
//        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, String.format("图像分辨率过高，宽/高大于4096像素，当前宽：%d，高：%d",
//                    width, height));
//        }
    }

    /**
     * 清理分页查询缓存 - Cache-Aside模式的核心
     */
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    public void clearPageCache() {
        // 第一次删除所有分页查询相关的缓存
        try {
            // 清理Redis缓存
            Set<String> keys = stringRedisTemplate.keys("leopicture:listPictureVOByPage:*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
            // 第二次延迟删除
            scheduledExecutorService.schedule(() -> {
                try {
                    Set<String> delayedKeys = stringRedisTemplate.keys("leopicture:listPictureVOByPage:*");
                    if (delayedKeys != null && !delayedKeys.isEmpty()) {
                        // Redis删除操作，执行很快，一个线程即可
                        stringRedisTemplate.delete(delayedKeys);
                        log.debug("延迟删除缓存完成");
                    }
                } catch (Exception e) {
                    log.error("延迟删除缓存失败", e);
                }
            }, 500, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("清理分页缓存失败", e);
        }
    }

    // 在类中添加销毁方法
    @PreDestroy
    public void destroy() {
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }
    }

    /**
     * nameRule 格式：图片-{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    @Override
    public String generateImageByText(GenerateImageRequest generateImageRequest, User loginUser) {
        // 验证请求参数
        if (generateImageRequest == null || loginUser == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数或用户信息为空");
        }
        
        String prompt = generateImageRequest.getPrompt();
        if (StrUtil.isBlank(prompt)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图像描述不能为空");
        }
        
//        // 校验空间权限（如果提供了空间ID）
//        Long spaceId = generateImageRequest.getSpaceId();
//        if (spaceId != null) {
//            Space space = spaceService.getById(spaceId);
//            if (space == null) {
//                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "指定的空间不存在");
//            }
//            // 检查用户是否有权限在该空间生成图片
//            boolean hasPermission = spaceUserAuthManager.hasPermission(loginUser.getId(), spaceId,
//                    SpaceUserPermissionConstant.PICTURE_UPLOAD);
//            if (!hasPermission) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "您没有权限在该空间生成图片");
//            }
//        }
        
        try {
            // 调用图片生成工具
            log.info("用户 {} 开始生成图片，提示词: {}", loginUser.getId(), prompt);
            String result = imageGenerationTool.generateImages(prompt, String.valueOf(loginUser.getId()), loginUser.getUserAccount());
            log.info("用户 {} 图片生成完成，结果: {}", loginUser.getId(), result);
            return result;
        } catch (Exception e) {
            log.error("用户 {} 图片生成失败", loginUser.getId(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片生成失败: " + e.getMessage());
        }
    }

}


