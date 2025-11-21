package com.leo.leopicturebackend.exception;

/**
 * 自定义短信异常类
 */
public  class SmsException extends Exception {
    public SmsException(String message) {
        super(message);
    }

    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }
}

