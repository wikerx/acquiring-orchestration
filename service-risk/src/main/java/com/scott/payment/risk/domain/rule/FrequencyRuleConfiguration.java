package com.scott.payment.risk.domain.rule;

import lombok.Data;

import java.util.List;

/**
 * 承载交易频率规则的 JSON 扩展配置。
 *
 * <p>Fastjson2 默认忽略未声明字段，因此规则平台可以向后兼容地增加扩展属性。
 * 已声明字段仍使用强类型反序列化，类型错误由策略解析器标记为无效配置。</p>
 */
@Data
public class FrequencyRuleConfiguration {

    /**
     * 参与频率统计的交易元素编码。
     */
    private List<String> elements;

    /**
     * 统计维度，支持 {@code ELEMENT_COMBINATION} 和 {@code ANY_ELEMENT}。
     */
    private String statisticDimension;

    /**
     * 固定窗口内允许的最大请求笔数。
     */
    private Integer allowedCount;

    /**
     * 固定窗口内允许的最大成功笔数，零表示不启用成功笔数限制。
     */
    private Integer successCount;

    /**
     * 固定统计窗口秒数。
     */
    private Integer timeWindowSeconds;
}
