package com.scott.payment.component.db.sharding;

import org.apache.shardingsphere.infra.hint.HintManager;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionPrimaryRouteScope
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 受控强一致读取作用域，在关闭时清理 ShardingSphere Hint，避免线程复用导致后续请求误走主库。
 * @status : create
 */
public final class TransactionPrimaryRouteScope implements AutoCloseable {

    /** 当前线程的 ShardingSphere 路由 Hint，必须随作用域关闭。 */
    private final HintManager hintManager;

    /** 仅允许通过 {@link #open()} 创建，保证构造完成前已启用主库路由。 */
    private TransactionPrimaryRouteScope(HintManager hintManager) {
        this.hintManager = hintManager;
    }

    /**
     * 打开主库强一致读取作用域。
     *
     * @return 必须使用 try-with-resources 关闭的作用域
     */
    public static TransactionPrimaryRouteScope open() {
        HintManager hintManager = HintManager.getInstance();
        hintManager.setWriteRouteOnly();
        return new TransactionPrimaryRouteScope(hintManager);
    }

    /** 清理线程本地 Hint，避免线程池复用时污染后续普通读请求。 */
    @Override
    public void close() {
        hintManager.close();
    }
}
