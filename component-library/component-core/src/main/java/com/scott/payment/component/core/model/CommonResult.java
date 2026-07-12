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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CommonResult
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Common Result，位于 component-library/component-core 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
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

    /**
     * 使用默认成功码构建成功响应。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return 成功响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param data 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> success(T data) {
        return success(ApiResultEnum.SUCCESS, data);
    }

    /**
     * 使用指定结果枚举构建成功响应。
     *
     * @param result 业务结果枚举
     * @param data   响应数据
     * @param <T>    响应数据类型
     * @return 成功响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param result 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param data 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> success(IResult result, T data) {
        return success(result.getCode(), result.getMessage(), data);
    }

    /**
     * 使用指定响应码和响应说明构建成功响应。
     *
     * @param code    响应码
     * @param message 响应说明
     * @param data    响应数据
     * @param <T>     响应数据类型
     * @return 成功响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param code 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param message 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param data 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> success(String code, String message, T data) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 构建无数据成功响应。
     *
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> success() {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(ApiResultEnum.SUCCESS.getCode());
        result.setMessage(ApiResultEnum.SUCCESS.getMessage());
        return result;
    }

    /**
     * 使用指定结果枚举构建无数据成功响应。
     *
     * @param resultEnum 业务结果枚举
     * @param <T>        响应数据类型
     * @return 成功响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param resultEnum 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> success(IResult resultEnum) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(resultEnum.getCode());
        result.setMessage(resultEnum.getMessage());
        return result;
    }

    /**
     * 将已有响应转换为错误响应，保留原响应码和说明。
     *
     * @param result 已有响应
     * @param <T>    新响应数据类型
     * @return 错误响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param result 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> error(CommonResult<?> result) {
        return error(result.getCode(), result.getMessage());
    }

    /**
     * 使用结果枚举构建错误响应。
     *
     * @param resultEnum 业务结果枚举
     * @param <T>        响应数据类型
     * @return 错误响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param resultEnum 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> error(IResult resultEnum) {
        return error(resultEnum.getCode(), resultEnum.getMessage());
    }

    /**
     * 使用指定错误码和错误说明构建错误响应。
     *
     * @param code    错误码
     * @param message 错误说明
     * @param <T>     响应数据类型
     * @return 错误响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param code 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param message 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> error(String code, String message) {
        if (ErrorCode.SUCCESS.equals(code) || ApiResultEnum.SUCCESS.getCode().equals(code)) {
            throw new IllegalArgumentException("code must be an error code");
        }
        CommonResult<T> result = new CommonResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 将业务异常转换为错误响应。
     *
     * @param exception 业务异常
     * @param <T>       响应数据类型
     * @return 错误响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param exception 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> error(BizException exception) {
        return error(exception.getCode(), exception.getMessage());
    }

    /**
     * 将服务异常转换为错误响应。
     *
     * @param exception 服务异常
     * @param <T>       响应数据类型
     * @return 错误响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param exception 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> CommonResult<T> error(ServiceException exception) {
        return error(exception.getCode(), exception.getMessage());
    }

    /**
     * 判断响应是否成功且数据不为空。
     *
     * @param result 响应对象
     * @return true 表示响应成功且存在数据
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param result 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static boolean resultNonNull(CommonResult<?> result) {
        return isSuccess(result) && Objects.nonNull(result.getData());
    }

    /**
     * 判断响应是否成功。
     *
     * @param result 响应对象
     * @return true 表示响应码为系统成功码
     */
    /**
     * 判断收单支付条件是否满足，供业务分支或权限控制使用。
     * @param result 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static boolean isSuccess(CommonResult<?> result) {
        return Objects.nonNull(result)
                && (Objects.equals(ErrorCode.SUCCESS, result.getCode())
                || Objects.equals(ApiResultEnum.SUCCESS.getCode(), result.getCode()));
    }
}
