package com.scott.payment.component.core.id;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdGenerator
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 全系统统一唯一标识生成器。 <p>生成规则：yyMMddHHmmssSSS + SSSSSS + C，总长度固定 22 位纯数字。</p>
 * @status : create
 */
public interface GlobalIdGenerator {

    /**
     * 生成全系统统一唯一标识。
     *
     * @return 22 位纯数字唯一标识
     */
    String nextId();
}
