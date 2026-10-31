package com.scott.payment.component.core.openapi.result;

import java.util.Map;
import java.util.Set;

/**
 * OpenAPI V1.3 单个公开码的内部治理定义。
 *
 * <p>定义集中登记适用契约、事实证据、展示模板、映射规则和生命周期，避免各服务自行解释同一公开码。</p>
 *
 * @param resultCode 已发布的五位公开码
 * @param meaning 稳定含义和触发条件
 * @param exclusions 明确不适用的相邻场景
 * @param applicableContracts 可使用该定义的响应位置或业务契约
 * @param phase 结果产生阶段
 * @param origin 事实来源
 * @param messages 五类已发布受众及语言模板
 * @param allowedParameters 模板允许使用的服务端受控参数
 * @param evidence 必须具备的事实证据类型
 * @param handlingPolicy 客户端和运营处理策略
 * @param dataContract 对原接口 data 和交易对象的约束
 * @param mappingRules 旧码或内部原因到该定义的条件规则
 * @param owner 维护责任域
 * @param version 首次发布规范版本
 * @param lifecycle 当前生命周期；REGISTERED 表示已进入公开字典但尚无已验证的生产触发路径
 */
public record OpenApiResultDefinition(OpenApiResultCode resultCode,
                                      String meaning,
                                      String exclusions,
                                      Set<String> applicableContracts,
                                      OpenApiResultPhase phase,
                                      OpenApiResultOrigin origin,
                                      Map<String, String> messages,
                                      Set<String> allowedParameters,
                                      OpenApiResultEvidence evidence,
                                      OpenApiHandlingPolicy handlingPolicy,
                                      String dataContract,
                                      Set<String> mappingRules,
                                      String owner,
                                      String version,
                                      OpenApiResultLifecycle lifecycle) {

    /**
     * 创建不可变定义，防止运行期修改已发布码表。
     **/
    public OpenApiResultDefinition {
        applicableContracts = Set.copyOf(applicableContracts);
        messages = Map.copyOf(messages);
        allowedParameters = Set.copyOf(allowedParameters);
        mappingRules = Set.copyOf(mappingRules);
    }
}
