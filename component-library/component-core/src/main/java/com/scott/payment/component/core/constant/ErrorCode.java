package com.scott.payment.component.core.constant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ErrorCode
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 系统错误码常量定义
 * @status : create
 */
public final class ErrorCode {

    /**
     * 通用成功码，主要用于内部简单接口和基础组件返回成功结果。
     */
    public static final String SUCCESS = "SUCCESS";

    /**
     * 系统错误码，表示服务内部出现未知异常或不可恢复错误。
     */
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";

    /**
     * 参数错误码，表示请求参数缺失、格式不合法或业务校验未通过。
     */
    public static final String PARAM_INVALID = "PARAM_INVALID";

    /**
     * 签名错误码，表示请求签名、JWT 或商户密钥校验失败。
     */
    public static final String SIGN_INVALID = "SIGN_INVALID";

    /**
     * 重放请求错误码，表示相同 nonce、jti 或业务唯一键在有效窗口内重复提交。
     */
    public static final String REPLAY_REQUEST = "REPLAY_REQUEST";

    private ErrorCode() {
    }
}
