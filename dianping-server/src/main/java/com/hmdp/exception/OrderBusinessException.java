package com.hmdp.exception;

/**
 * 订单业务异常
 *
 * @author 王天一
 * @version 1.0
 */
public class OrderBusinessException extends BaseException {
    public OrderBusinessException() {
    }

    public OrderBusinessException(String msg) {
        super(msg);
    }
}
