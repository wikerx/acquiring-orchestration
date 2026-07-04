package com.scott.payment.component.core.auth;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalAuthContextHolder
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 内部管理类接口登录账号上下文持有器
 * @status : create
 */
public final class InternalAuthContextHolder {

    /**
     * 当前线程登录账号上下文。
     */
    private static final ThreadLocal<InternalAuthAccount> CONTEXT = new ThreadLocal<>();

    private InternalAuthContextHolder() {
    }

    /**
     * 写入当前登录账号上下文。
     *
     * @param account 当前登录账号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param account 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public static void set(InternalAuthAccount account) {
        CONTEXT.set(account);
    }

    /**
     * 获取当前登录账号上下文。
     *
     * @return 当前登录账号
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static InternalAuthAccount get() {
        return CONTEXT.get();
    }

    /**
     * 清理当前线程上下文，避免线程复用导致数据串用。
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
