package com.scott.payment.component.core.id;

/**
 * 全系统统一唯一标识生成器。
 *
 * <p>生成规则：yyMMddHHmmssSSS + SSSSSS + C，总长度固定 22 位纯数字。</p>
 */
public interface GlobalIdGenerator {

    /**
     * 生成全系统统一唯一标识。
     *
     * @return 22 位纯数字唯一标识
     */
    String nextId();
}
