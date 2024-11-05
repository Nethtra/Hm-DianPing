package com.hmdp.handler;

import com.hmdp.dto.Result;
import exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author 王天一
 * @version 1.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler
    public Result exceptionHandler(BaseException exception) {
        log.info("全局异常处理器捕获yichang{}", exception.getMessage());//记录log
        return Result.fail(exception.getMessage());//返回
    }
}