package com.hmdp.exception;

/**
 * 自定义异常的父类
 *
 * @author 王天一
 * @version 1.0
 */
public class BaseException extends RuntimeException {
    public BaseException() {
    }

    public BaseException(String msg) {
        super(msg);
    }
}
