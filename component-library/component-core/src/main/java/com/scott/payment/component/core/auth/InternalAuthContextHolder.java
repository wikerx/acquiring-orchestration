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
    public static void set(InternalAuthAccount account) {
        CONTEXT.set(account);
    }

    /**
     * 获取当前登录账号上下文。
     *
     * @return 当前登录账号
     */
    public static InternalAuthAccount get() {
        return CONTEXT.get();
    }

    /**
     * 清理当前线程上下文，避免线程复用导致数据串用。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
