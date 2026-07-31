package com.scott.payment.risk.config;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskRuleCacheMode
 * @date : 2026-07-31 15:40
 * @email : scott_x@163.com
 * @description : 风控名单与规则缓存迁移模式，控制旧请求级缓存和完整常驻快照的决策关系
 * @status : create
 */
public enum RiskRuleCacheMode {

    /**
     * 继续使用带代际的请求级短 TTL 缓存，用于尚未进入快照观察的环境。
     */
    LEGACY,

    /**
     * 旧缓存继续参与真实决策，同时构建并比较完整快照，不改变交易结果。
     */
    SHADOW,

    /**
     * 使用完整常驻快照参与真实决策；快照不可用或越界时安全回源主库。
     */
    SNAPSHOT
}
