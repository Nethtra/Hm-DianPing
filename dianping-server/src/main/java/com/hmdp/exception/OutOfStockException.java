package com.hmdp.exception;

/**
 * 缺货异常
 *
 * @author 王天一
 * @version 1.0
 */
public class OutOfStockException extends BaseException {
    public OutOfStockException() {
    }

    public OutOfStockException(String msg) {
        super(msg);
    }
}
