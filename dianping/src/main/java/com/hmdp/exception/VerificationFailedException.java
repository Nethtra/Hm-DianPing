package com.hmdp.exception;

/**
 * 校验失败异常
 * 用于用户名 验证码 密码错误
 *
 * @author 王天一
 * @version 1.0
 */
public class VerificationFailedException extends BaseException {
    public VerificationFailedException() {
    }

    public VerificationFailedException(String msg) {
        super(msg);
    }
}
