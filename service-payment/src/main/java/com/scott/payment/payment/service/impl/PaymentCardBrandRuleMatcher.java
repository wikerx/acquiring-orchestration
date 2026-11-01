package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.reference.model.CardBrandRuleMatcher;

/**
 * 支付侧卡品牌规则兼容入口。
 *
 * <p>实际规则委托给支付与 OpenAPI 共用的公开 IIN 匹配器，避免两端维护不同品牌范围；
 * 该兜底结果不能替代权威卡 BIN 数据服务。</p>
 */
final class PaymentCardBrandRuleMatcher {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private PaymentCardBrandRuleMatcher() {
    }

    /**
     * 根据纯数字 BIN 或完整卡号识别平台标准卡品牌。
     *
     * <p>存在品牌范围重叠时，按更具体的 Discover、UnionPay、Mastercard 规则优先于 Maestro 处理。</p>
     *
     * @param cardDigits 纯数字 BIN 或完整卡号
     * @return 平台标准卡品牌；输入为空或包含非数字字符时返回 {@code UNKNOWN}
     */
    static String resolve(String cardDigits) {
        return CardBrandRuleMatcher.resolve(cardDigits);
    }
}
