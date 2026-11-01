package com.scott.payment.component.core.id;


/**
 * globalIDgenerator协作组件，位于 公共组件库，封装该业务的本地校验、转换或运行时协作入口。
 */
public interface GlobalIdGenerator {

    /**
     * 生成全系统统一唯一标识。
     *
     * @return 22 位纯数字唯一标识
     */
    String nextId();
}
