package com.fishingtime.common.exception;

import com.fishingtime.common.dto.ErrorCode;
import lombok.Getter;

/**
 * 业务异常 — 可控的流程异常，返回指定错误码
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
    }
}
