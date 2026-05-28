package com.scott.payment.component.core.model;

import com.scott.payment.component.core.constant.ErrorCode;
import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.BizException;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.result.IResult;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CommonResult
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 对外 API 通用响应结果
 * @status : create
 */
@Data
public class CommonResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;
    private String message;
    private T data;

    public static <T> CommonResult<T> success(T data) {
        return success(ApiCoResultEnum.SUCCESS, data);
    }

    public static <T> CommonResult<T> success(IResult result, T data) {
        return success(result.getCode(), result.getMessage(), data);
    }

    public static <T> CommonResult<T> success(String code, String message, T data) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> CommonResult<T> success() {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(ApiCoResultEnum.SUCCESS.getCode());
        result.setMessage(ApiCoResultEnum.SUCCESS.getMessage());
        return result;
    }

    public static <T> CommonResult<T> success(IResult resultEnum) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(resultEnum.getCode());
        result.setMessage(resultEnum.getMessage());
        return result;
    }

    public static <T> CommonResult<T> error(CommonResult<?> result) {
        return error(result.getCode(), result.getMessage());
    }

    public static <T> CommonResult<T> error(IResult resultEnum) {
        return error(resultEnum.getCode(), resultEnum.getMessage());
    }

    public static <T> CommonResult<T> error(String code, String message) {
        if (ErrorCode.SUCCESS.equals(code) || ApiCoResultEnum.SUCCESS.getCode().equals(code)) {
            throw new IllegalArgumentException("code must be an error code");
        }
        CommonResult<T> result = new CommonResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> CommonResult<T> error(BizException exception) {
        return error(exception.getCode(), exception.getMessage());
    }

    public static <T> CommonResult<T> error(ServiceException exception) {
        return error(exception.getCode(), exception.getMessage());
    }

    public static boolean resultNonNull(CommonResult<?> result) {
        return isSuccess(result) && Objects.nonNull(result.getData());
    }

    public static boolean isSuccess(CommonResult<?> result) {
        return Objects.nonNull(result)
                && (Objects.equals(ErrorCode.SUCCESS, result.getCode())
                || Objects.equals(ApiCoResultEnum.SUCCESS.getCode(), result.getCode()));
    }
}
