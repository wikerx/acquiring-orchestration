package com.scott.payment.openapi.result;

import com.scott.payment.component.core.openapi.result.OpenApiResultEvidence;
import com.scott.payment.component.core.openapi.result.OpenApiResultOrigin;
import com.scott.payment.component.core.openapi.result.OpenApiResultPhase;

import java.util.Map;

/**
 * OpenAPI 异常投影所需的受控事实上下文。
 *
 * <p>上下文只记录阶段、来源、确定性和安全模板参数，不保存异常原文、请求明文或渠道敏感报文。</p>
 *
 * @param phase 失败发生阶段
 * @param origin 可验证的失败来源
 * @param requestSent 请求是否已越过发送边界；未知时为 null
 * @param outcomeUncertain 业务结果是否仍不确定；未知时为 null
 * @param fieldPath 已登记的请求字段路径；无字段级错误时为 null
 * @param messageParameters 服务端生成的安全模板参数
 * @param evidence 支撑本次归类的证据类型
 */
public record OpenApiErrorContext(OpenApiResultPhase phase,
                                  OpenApiResultOrigin origin,
                                  Boolean requestSent,
                                  Boolean outcomeUncertain,
                                  String fieldPath,
                                  Map<String, String> messageParameters,
                                  OpenApiResultEvidence evidence) {

    /**
     * 防止调用方在异常创建后修改模板参数，造成日志与响应投影不一致。
     */
    public OpenApiErrorContext {
        messageParameters = messageParameters == null ? Map.of() : Map.copyOf(messageParameters);
    }
}
