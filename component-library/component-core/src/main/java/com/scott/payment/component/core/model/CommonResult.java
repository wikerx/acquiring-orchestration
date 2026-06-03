package com.scott.payment.component.core.model;

import com.scott.payment.component.core.constant.ErrorCode;
import com.scott.payment.component.core.enums.ApiResultEnum;
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

    /**
     * 序列化版本号，用于保证统一响应对象在服务间传输或日志落库时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 业务响应码，对外 API 使用 T/F/Z 等业务码，内部接口可使用基础错误码。
     */
    private String code;

    /**
     * 响应描述，成功时返回成功信息，失败时返回商户或调用方可理解的错误说明。
     */
    private String message;

    /**
     * 响应数据载荷，成功时承载业务结果，失败时通常为空。
     */
    private T data;

    public static <T> CommonResult<T> success(T data) {
        return success(ApiResultEnum.SUCCESS, data);
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
        result.setCode(ApiResultEnum.SUCCESS.getCode());
        result.setMessage(ApiResultEnum.SUCCESS.getMessage());
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
        if (ErrorCode.SUCCESS.equals(code) || ApiResultEnum.SUCCESS.getCode().equals(code)) {
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
                || Objects.equals(ApiResultEnum.SUCCESS.getCode(), result.getCode()));
    }
}
