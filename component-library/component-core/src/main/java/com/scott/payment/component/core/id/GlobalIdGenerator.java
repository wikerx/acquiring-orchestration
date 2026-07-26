package com.scott.payment.component.core.id;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdGenerator
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : GlobalIdGenerator Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
