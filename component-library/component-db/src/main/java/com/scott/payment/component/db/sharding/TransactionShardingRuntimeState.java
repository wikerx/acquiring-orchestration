package com.scott.payment.component.db.sharding;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingRuntimeState
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 保存当前实例已加载的交易分片模式、规则版本和校验和前缀，供健康检查与治理接口读取。
 * @status : create
 */
public class TransactionShardingRuntimeState {

    /** 当前实例是否已成功装配 ShardingSphere 复合数据源。 */
    private volatile boolean active;
    /** 实际加载的单写切换模式。 */
    private volatile String mode = "LEGACY";
    /** 实际通过完整性校验的规则版本。 */
    private volatile String ruleVersion = "unpublished";
    /** 用于健康检查比对的非敏感 checksum 前缀。 */
    private volatile String checksumPrefix = "unpublished";

    /**
     * 在复合数据源注册成功后发布运行状态。
     *
     * @param properties 已通过激活校验的规则
     */
    public void activate(TransactionShardingProperties properties) {
        load(properties, true);
    }

    void loadLegacy(TransactionShardingProperties properties) {
        load(properties, false);
    }

    /** 以一次性字段替换记录实际装载结果，供 Actuator 和管理端读取。 */
    private void load(TransactionShardingProperties properties, boolean compositeDataSourceActive) {
        active = compositeDataSourceActive;
        mode = properties.getMode().name();
        ruleVersion = properties.getRuleVersion();
        String checksum = properties.getRuleChecksum();
        checksumPrefix = checksum == null ? "unpublished" : checksum.substring(0, Math.min(checksum.length(), 19));
    }

    /** @return 是否实际装配了 ShardingSphere 复合数据源 */
    public boolean isActive() {
        return active;
    }

    /** @return 实际加载的切换模式 */
    public String getMode() {
        return mode;
    }

    /** @return 实际校验通过的规则版本 */
    public String getRuleVersion() {
        return ruleVersion;
    }

    /** @return 可公开用于实例间比对的 checksum 前缀 */
    public String getChecksumPrefix() {
        return checksumPrefix;
    }

    /**
     * 判断当前实例是否允许业务写路径使用交易逻辑数据源。
     *
     * @return 仅 SHARDINGSPHERE 模式返回 true
     */
    public boolean isShardingWriteEnabled() {
        return TransactionShardingMode.SHARDINGSPHERE.name().equals(mode);
    }

    /**
     * 判断当前实例是否只允许执行旧路径与逻辑路径的只读结果比对。
     *
     * @return COMPARE 模式返回 true
     */
    public boolean isReadComparisonEnabled() {
        return TransactionShardingMode.COMPARE.name().equals(mode);
    }
}
