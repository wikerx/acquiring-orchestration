package com.scott.payment.component.core.id;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdGenerator
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : globalIDgenerator协作组件，位于 公共组件库，封装该业务的本地校验、转换或运行时协作入口。
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
