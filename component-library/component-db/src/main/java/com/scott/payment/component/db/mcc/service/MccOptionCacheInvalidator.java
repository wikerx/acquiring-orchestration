package com.scott.payment.component.db.mcc.service;

/**
 * 公共 MCC 选项缓存失效契约，供管理端写事务登记提交后可靠失效
 */
public interface MccOptionCacheInvalidator {

    /**
     * 在 MCC 管理事务内登记公共选项快照的可靠失效。
     *
     * <p>事务提交后由统一失效协调器删除缓存；事务回滚时不得删除现有快照。</p>
     */
    void evictOptions();
}
