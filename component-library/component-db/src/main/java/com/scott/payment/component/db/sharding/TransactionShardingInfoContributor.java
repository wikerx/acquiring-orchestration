package com.scott.payment.component.db.sharding;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingInfoContributor
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 在五个交易直连服务的 Actuator info 中暴露非敏感规则版本、checksum 前缀和当前路由模式。
 * @status : create
 */
@Component
public class TransactionShardingInfoContributor implements InfoContributor {

    /** 当前实例绑定的版本化交易分片配置。 */
    private final TransactionShardingProperties properties;
    /** 数据源实际装载后的运行状态，避免只报告配置期望值。 */
    private final TransactionShardingRuntimeState runtimeState;
    /** 用于限定仅五个交易直连服务暴露治理信息。 */
    private final Environment environment;

    /**
     * 创建非敏感治理信息贡献器。
     *
     * @param properties 版本化分片配置
     * @param runtimeState 实例实际装载状态
     * @param environment 当前服务运行环境
     */
    public TransactionShardingInfoContributor(TransactionShardingProperties properties,
                                               TransactionShardingRuntimeState runtimeState,
                                               Environment environment) {
        this.properties = properties;
        this.runtimeState = runtimeState;
        this.environment = environment;
    }

    /**
     * 为直接访问交易逻辑数据源的服务补充可比对规则信息，非直连服务不输出该节点。
     *
     * @param builder Actuator info 构造器
     */
    @Override
    public void contribute(Info.Builder builder) {
        String applicationName = environment.getProperty("spring.application.name", "");
        if (!properties.getDirectAccessServices().contains(applicationName)) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mode", runtimeState.getMode());
        details.put("ruleVersion", runtimeState.getRuleVersion());
        details.put("ruleChecksumPrefix", runtimeState.getChecksumPrefix());
        details.put("compositeDataSourceActive", runtimeState.isActive());
        builder.withDetail("transactionSharding", details);
    }
}
