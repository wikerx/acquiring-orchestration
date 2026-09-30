package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.reference.model.CardBrandRuleMatcher;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCardBrandRuleMatcher
 * @date : 2026-08-11 15:10
 * @email : scott_x@163.com
 * @description : 保留支付侧原调用点，委托支付与 OpenAPI 共用的公开 IIN 卡品牌兜底规则。
 * @status : create
 */
final class PaymentCardBrandRuleMatcher {

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
