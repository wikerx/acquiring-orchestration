package com.scott.payment.openapi.result;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.openapi.result.OpenApiMessageLocale;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.openapi.result.OpenApiResultMessageCatalog;

import java.util.Map;

/**
 * 商户 OpenAPI 外层响应工厂。
 *
 * <p>外层结果只表达请求处理情况，交易结果保留在加密 data 的专属字段中；展示对象由服务端入口确定，
 * 禁止客户端通过参数获取 INTERNAL 级别文案。</p>
 */
public final class OpenApiResponseFactory {

    private OpenApiResponseFactory() {
    }

    /**
     * 构造已形成约定业务 data 的正常请求响应。
     *
     * @param data 接口原有业务响应，交易成功与否由其专属状态字段表达
     * @param <T>  业务响应类型
     * @return 外层固定为 A0000 的响应
     */
    public static <T> CommonResult<T> success(T data) {
        return success(data, OpenApiMessageAudience.MERCHANT);
    }

    /**
     * 按服务端可信入口构造请求成功响应；受众由控制器命名空间固定，不能由客户端参数选择。
     * 对外协议语言固定为英文，页面 locale 不得改变 API 或回跳表单契约。
     *
     * @param data 原接口业务响应
     * @param audience 可信展示对象
     * @param <T> 业务响应类型
     * @return 外层固定为 A0000 的响应
     */
    public static <T> CommonResult<T> success(T data,
                                               OpenApiMessageAudience audience) {
        CommonResult<T> result = CommonResult.success(OpenApiResultCode.REQUEST_SUCCESS, data);
        result.setMessage(OpenApiResultMessageCatalog.message(
                OpenApiResultCode.REQUEST_SUCCESS,
                audience, OpenApiMessageLocale.EN_US));
        return result;
    }

    /**
     * 构造没有业务 data 的 OpenAPI 错误响应。
     *
     * @param resultCode 已登记的五位公开码
     * @param <T>        响应 data 类型
     * @return 使用安全默认文案的错误响应
     */
    public static <T> CommonResult<T> error(OpenApiResultCode resultCode) {
        return error(resultCode, OpenApiMessageAudience.MERCHANT);
    }

    /**
     * 按可信入口对象构造错误响应，不接受浏览器或商户传入的 audience 提权。
     *
     * @param resultCode 已登记公开码
     * @param audience 服务端确定的展示对象
     * @param <T> 响应 data 类型
     * @return 使用对应对象安全文案的错误响应
     */
    public static <T> CommonResult<T> error(OpenApiResultCode resultCode,
                                             OpenApiMessageAudience audience) {
        return error(resultCode, audience, Map.of());
    }

    /**
     * 使用异常投影中的受控参数构造错误响应；文案目录会再次按受众过滤参数。
     *
     * @param projection 已完成来源判定的异常投影
     * @param audience 服务端确定的展示对象
     * @param <T> 响应 data 类型
     * @return 不泄漏内部诊断参数的错误响应
     */
    public static <T> CommonResult<T> error(OpenApiErrorProjection projection,
                                             OpenApiMessageAudience audience) {
        return error(projection.resultCode(), audience, projection.messageParameters());
    }

    /**
     * 构造最终错误响应，并由文案目录执行受众隔离、参数过滤和安全回退。
     */
    private static <T> CommonResult<T> error(OpenApiResultCode resultCode,
                                              OpenApiMessageAudience audience,
                                              Map<String, String> messageParameters) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(resultCode.getCode());
        result.setMessage(OpenApiResultMessageCatalog.message(
                resultCode, audience, OpenApiMessageLocale.EN_US, messageParameters));
        return result;
    }
}
