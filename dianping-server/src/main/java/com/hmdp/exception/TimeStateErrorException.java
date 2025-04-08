package com.hmdp.exception;

/**
 * 时间错误异常
 * 未到开始时间/已到结束时间
 *
 * @author 王天一
 * @version 1.0
 */
public class TimeStateErrorException extends BaseException {
    public TimeStateErrorException() {
    }

    public TimeStateErrorException(String msg) {
        super(msg);
    }
}
