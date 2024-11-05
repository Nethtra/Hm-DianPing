package exception;

import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * 格式不合法异常
 *
 * @author 王天一
 * @version 1.0
 */
public class InvalidFormatException extends BaseException {
    public InvalidFormatException() {
    }

    public InvalidFormatException(String msg) {
        super(msg);
    }
}
