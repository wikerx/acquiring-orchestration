package com.scott.payment.component.db.sharding;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingMode
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 定义交易分片单写切换模式，确保只读比对不会意外开放 ShardingSphere 写路径。
 * @status : create
 */
public enum TransactionShardingMode {

    /** 业务继续走旧物理表路径，仅装配 transaction 主库别名用于兼容。 */
    LEGACY(false, false),
    /** 新旧路径只读比对，禁止通过 ShardingSphere 写入。 */
    COMPARE(true, false),
    /** 业务读写均由 ShardingSphere 单写接管。 */
    SHARDINGSPHERE(true, true);

    /** 是否需要创建含分片和读写分离规则的复合数据源。 */
    private final boolean compositeDataSourceRequired;
    /** 是否允许业务写路径选择 transaction 逻辑数据源。 */
    private final boolean shardingWriteAllowed;

    TransactionShardingMode(boolean compositeDataSourceRequired, boolean shardingWriteAllowed) {
        this.compositeDataSourceRequired = compositeDataSourceRequired;
        this.shardingWriteAllowed = shardingWriteAllowed;
    }

    /**
     * 判断当前模式是否需要装配 ShardingSphere 复合数据源。
     *
     * @return COMPARE 和 SHARDINGSPHERE 返回 true
     */
    public boolean isCompositeDataSourceRequired() {
        return compositeDataSourceRequired;
    }

    /**
     * 判断业务写路径是否允许选择 ShardingSphere。
     *
     * @return 仅 SHARDINGSPHERE 返回 true
     */
    public boolean isShardingWriteAllowed() {
        return shardingWriteAllowed;
    }

    /**
     * 判断是否处于只读结果比对模式。
     *
     * @return 仅 COMPARE 返回 true
     */
    public boolean isReadComparisonEnabled() {
        return this == COMPARE;
    }
}
