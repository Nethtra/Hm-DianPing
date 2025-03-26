package com.hmdp.exception;

/**
 * 数据不存在异常
 *
 * 用于未查找到数据库中的数据时使用
 *
 * @author 王天一
 * @version 1.0
 */
public class DataNotFoundException extends BaseException {
    public DataNotFoundException(String msg) {
        super(msg);
    }
}
