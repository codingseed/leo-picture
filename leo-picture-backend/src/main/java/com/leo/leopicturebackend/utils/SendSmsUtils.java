package com.leo.leopicturebackend.utils;

//接口类型：互亿无线触发短信接口，支持发送验证码短信、订单通知短信等。
// 账户注册：请通过该地址开通账户https://user.ihuyi.com/new/register.html
// 注意事项：
//（1）调试期间，请使用系统默认的短信内容：您的验证码是：【变量】。请不要把验证码泄露给其他人。
//（2）请使用 APIID 及 APIKEY来调用接口，可在会员中心获取；
//（3）该代码仅供接入互亿无线短信接口参考使用，客户可根据实际需要自行编写；

import com.leo.leopicturebackend.exception.ErrorCode;
import com.leo.leopicturebackend.exception.SmsException;
import com.leo.leopicturebackend.exception.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.Data;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Data
@Component
public class SendSmsUtils {
    private static final Logger logger = LoggerFactory.getLogger(SendSmsUtils.class);
    @Resource
    private RedisTemplate redisTemplate;
//    # 短信服务配置
    private static String URL  = "http://106.ihuyi.com/webservice/sms.php?method=Submit";
    private static String APIID = "C80922120";
    private static String APIKEY ="f18cdbd77b05ea407fc71e41b7b0e1f3";

    @Resource
    private SmsLimiter smsLimiter;
    /**
     * 发送验证码短信
     *
     * @param phoneNumber 手机号码
     * @throws SmsException 短信发送异常
     */
    public  void sendVerificationCode(String phoneNumber) throws SmsException {
        ThrowUtils.throwIf(phoneNumber == null || phoneNumber.isEmpty(), ErrorCode.PARAMS_ERROR, "手机号码不能为空");
        // 幂等性检查
        String idempotentKey = String.format("idempotent:%s",phoneNumber) ;
        Boolean hasKey = redisTemplate.hasKey(idempotentKey);
        if (Boolean.TRUE.equals(hasKey)) {
            throw new SmsException("验证码已发送，请查看短信或稍后再试");
        }
        int verificationCode = generateVerificationCode();
        // 使用redis来存储手机号和验证码，同时使用滑动窗口算法来实现流量控制
        smsLimiter.sendSmsAuth(phoneNumber, String.valueOf(verificationCode));
        String content = "您的验证码是：" + verificationCode + "。请不要把验证码泄露给其他人。";
        HttpClient client = new HttpClient();
        PostMethod method = new PostMethod(URL);
        try {
            // 设置请求参数
            client.getParams().setContentCharset("GBK");
            method.setRequestHeader("ContentType", "application/x-www-form-urlencoded;charset=GBK");
            NameValuePair[] data = {
                    new NameValuePair("account", APIID),
                    new NameValuePair("password", APIKEY),
                    new NameValuePair("mobile", phoneNumber),
                    new NameValuePair("content", content)
            };
            // 设置请求体
            method.setRequestBody(data);

            // 执行请求
            client.executeMethod(method);
            // 解析响应
            String response = method.getResponseBodyAsString();
            processResponse(response);
            logger.info("短信发送成功，手机号: {}, 验证码: {}", phoneNumber, verificationCode);

        } catch (HttpException e) {
            throw new SmsException("HTTP协议错误: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new SmsException("网络通信错误: " + e.getMessage(), e);
        } finally {
            method.releaseConnection();
        }
    }
    /**
     * 验证手机号是否符合要求
     * @param phone
     * @return
     */
    public boolean isPhoneNum(String phone){
        String regex = "^((13[0-9])|(14[5,7,9])|(15([0-3]|[5-9]))|(16[5,6])|(17[0-8])|(18[0-9])|(19[1、5、8、9]))\\d{8}$";
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(phone);
        return m.matches();
    }
    /**
     * 生成6位随机验证码
     */
    private static int generateVerificationCode() {
        double code=(Math.random() * 9 + 1) * 100000;
        return (int) (code);
    }

    /**
     * 处理短信接口响应
     */
    private static void processResponse(String response) throws SmsException {
        try {
            //DOM4J (Document Object Model for Java)。提供了一个简单、灵活且高性能的 API 来读取、修改、创建和操作 XML 文档
            Document doc = DocumentHelper.parseText(response);
            Element root = doc.getRootElement();

            String code = root.elementText("code");
            String msg = root.elementText("msg");
            String smsid = root.elementText("smsid");

            logger.debug("短信接口响应 - code: {}, msg: {}, smsid: {}", code, msg, smsid);

            if (!"2".equals(code)) {
                throw new SmsException("短信发送失败: " + msg + " (错误码: " + code + ")");
            }
        } catch (DocumentException e) {
            throw new SmsException("解析短信接口响应失败: " + e.getMessage(), e);
        }
    }
}
