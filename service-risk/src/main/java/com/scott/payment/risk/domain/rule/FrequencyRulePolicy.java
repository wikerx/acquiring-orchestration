package com.scott.payment.risk.domain.rule;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.domain.RiskListMatch;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 表示经过解析和基础校验的交易频率策略。
 *
 * <p>解析失败不会抛到支付主链路，而是通过 {@link #isExecutable(int, int)} 返回不可执行，
 * 由调用方按风控失败关闭原则映射为人工复核。</p>
 *
 * @param elements 参与统计的受支持元素
 * @param dimension 组合统计或任一元素统计
 * @param windowSeconds 固定窗口秒数
 * @param allowedCount 窗口允许的最大请求笔数
 * @param successCount 窗口允许的最大成功笔数
 * @param valid JSON、窗口、阈值和维度是否通过基础校验
 */
public record FrequencyRulePolicy(List<String> elements,
                                  String dimension,
                                  int windowSeconds,
                                  int allowedCount,
                                  int successCount,
                                  boolean valid) {

    /**
     * 将全部规则元素拼成一个联合统计维度。
     */
    public static final String DIMENSION_COMBINATION = "ELEMENT_COMBINATION";

    /**
     * 为每个可用规则元素分别统计，任一维度超限即触发规则。
     */
    public static final String DIMENSION_ANY = "ANY_ELEMENT";

    /**
     * 频率规则中代表卡号哈希的元素编码。
     */
    public static final String ELEMENT_CARD_NO = "cardNo";

    /**
     * 频率规则中代表稳定卡指纹的元素编码。
     */
    public static final String ELEMENT_CARD_FINGERPRINT = "cardFingerprint";

    /**
     * 频率规则中代表规范化 IP 摘要的元素编码。
     */
    public static final String ELEMENT_IP = "ip";

    /**
     * 频率规则中代表规范化邮箱哈希的元素编码。
     */
    public static final String ELEMENT_EMAIL = "email";

    /**
     * 频率规则中代表规范化手机号哈希的元素编码。
     */
    public static final String ELEMENT_PHONE = "phone";

    /**
     * 频率规则中代表商户客户标识哈希的元素编码。
     */
    public static final String ELEMENT_CUSTOMER_ID = "customerId";

    /**
     * 频率规则中代表设备指纹哈希的元素编码。
     */
    public static final String ELEMENT_DEVICE_FINGERPRINT = "deviceFingerprint";

    private static final Set<String> SUPPORTED_ELEMENTS = Set.of(
            ELEMENT_CARD_NO,
            ELEMENT_CARD_FINGERPRINT,
            ELEMENT_IP,
            ELEMENT_EMAIL,
            ELEMENT_PHONE,
            ELEMENT_CUSTOMER_ID,
            ELEMENT_DEVICE_FINGERPRINT
    );

    /**
     * 保护策略元素集合不被调用方修改。
     */
    public FrequencyRulePolicy {
        elements = elements == null ? List.of() : List.copyOf(elements);
    }

    /**
     * 从数据库规则字段和类型化 JSON 配置解析统一频控策略。
     *
     * <p>扩展 JSON 没有提供的字段沿用数据库列或安全默认值；已声明字段类型错误、
     * 非法维度和越界阈值均保留为不可执行结果，禁止静默回退后继续计数。</p>
     *
     * @param rule 数据库频率规则
     * @return 不向支付主链路抛出解析异常的策略结果
     */
    public static FrequencyRulePolicy from(RiskListMatch rule) {
        List<String> elements = new ArrayList<>();
        String dimension = DIMENSION_ANY;
        Integer allowedCount = rule == null ? null : rule.getThresholdCount();
        Integer successCount = 0;
        Integer windowSeconds = rule == null ? null : rule.getTimeWindowSeconds();
        boolean valid = rule != null;

        if (rule != null && StringUtils.hasText(rule.getElementsJson())) {
            try {
                FrequencyRuleConfiguration configuration = JsonUtils.parseObject(
                        rule.getElementsJson(), FrequencyRuleConfiguration.class);
                if (configuration != null) {
                    if (configuration.getElements() != null) {
                        for (String element : configuration.getElements()) {
                            if (StringUtils.hasText(element)) {
                                elements.add(element.trim());
                            }
                        }
                    }
                    if (StringUtils.hasText(configuration.getStatisticDimension())) {
                        dimension = configuration.getStatisticDimension().trim().toUpperCase(Locale.ROOT);
                        valid = DIMENSION_COMBINATION.equals(dimension) || DIMENSION_ANY.equals(dimension);
                    }
                    allowedCount = valueOrFallback(configuration.getAllowedCount(), allowedCount);
                    successCount = valueOrFallback(configuration.getSuccessCount(), successCount);
                    windowSeconds = valueOrFallback(configuration.getTimeWindowSeconds(), windowSeconds);
                }
            } catch (RuntimeException exception) {
                valid = false;
            }
        }

        if (elements.isEmpty()) {
            elements.add(ELEMENT_CARD_FINGERPRINT);
            elements.add(ELEMENT_IP);
        }
        int normalizedWindowSeconds = windowSeconds == null ? 3_600 : windowSeconds;
        int normalizedAllowedCount = allowedCount == null ? 1 : allowedCount;
        int normalizedSuccessCount = successCount == null ? 0 : successCount;
        return new FrequencyRulePolicy(
                elements.stream().distinct().sorted().toList(),
                DIMENSION_COMBINATION.equals(dimension) ? DIMENSION_COMBINATION : DIMENSION_ANY,
                normalizedWindowSeconds,
                normalizedAllowedCount,
                normalizedSuccessCount,
                valid
                        && normalizedWindowSeconds > 0
                        && normalizedAllowedCount > 0
                        && normalizedSuccessCount >= 0
                        && normalizedSuccessCount <= normalizedAllowedCount
        );
    }

    /**
     * 校验策略不会超过部署允许的窗口和阈值。
     *
     * @param maxWindowSeconds 部署允许的最大窗口秒数
     * @param maxThresholdCount 部署允许的最大阈值
     * @return 策略可以安全执行时返回 {@code true}
     */
    public boolean isExecutable(int maxWindowSeconds, int maxThresholdCount) {
        return valid
                && windowSeconds <= maxWindowSeconds
                && allowedCount <= maxThresholdCount
                && successCount <= maxThresholdCount
                && !elements.isEmpty()
                && SUPPORTED_ELEMENTS.containsAll(elements);
    }

    /**
     * 根据统计维度构造不可逆计数身份。
     *
     * @param elementValues 已规范化并准备再次摘要的元素值
     * @return 联合维度返回一个摘要，任一元素维度返回每个可用元素的摘要
     */
    public List<String> counterIdentities(Map<String, String> elementValues) {
        if (DIMENSION_COMBINATION.equals(dimension)) {
            List<String> parts = new ArrayList<>();
            for (String element : elements) {
                String value = elementValues.get(element);
                if (!StringUtils.hasText(value)) {
                    return List.of();
                }
                parts.add(element + "=" + value);
            }
            return List.of(RedisKeyDigest.sha256(String.join("&", parts)));
        }
        List<String> identities = new ArrayList<>();
        for (String element : elements) {
            String value = elementValues.get(element);
            if (StringUtils.hasText(value)) {
                identities.add(RedisKeyDigest.sha256(element + "=" + value));
            }
        }
        return identities;
    }

    /**
     * 生成只描述参与元素、不包含敏感值的审计摘要。
     *
     * @param elementValues 已规范化的元素值
     * @return 例如 {@code ip=HASH&cardFingerprint=HASH} 的脱敏说明
     */
    public String maskedHitValue(Map<String, String> elementValues) {
        return elements.stream()
                .filter(element -> StringUtils.hasText(elementValues.get(element)))
                .map(element -> element + "=HASH")
                .collect(Collectors.joining("&"));
    }

    /**
     * 优先使用扩展配置值，未声明时沿用数据库列或安全默认值。
     *
     * @param value 类型化扩展配置值
     * @param fallback 数据库列或安全默认值
     * @return 非空扩展值或回退值
     */
    private static Integer valueOrFallback(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }
}
