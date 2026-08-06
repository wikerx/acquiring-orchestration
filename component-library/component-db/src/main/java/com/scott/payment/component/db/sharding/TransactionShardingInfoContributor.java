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
 * @description : 在五个交易直连服务的 Actuator info 中暴露非敏感规则版本、checksum 前缀和固定 ShardingSphere 架构状态。
 * @status : create
 */
@Component
public class TransactionShardingInfoContributor implements InfoContributor {

    /** 当前实例绑定的版本化交易分片配置。 */
    private final TransactionShardingProperties properties;
    /** 用于限定仅五个交易直连服务暴露治理信息。 */
    private final Environment environment;

    /**
     * 创建非敏感治理信息贡献器。
     *
     * @param properties 版本化分片配置
     * @param environment 当前服务运行环境
     */
    public TransactionShardingInfoContributor(TransactionShardingProperties properties,
                                               Environment environment) {
        this.properties = properties;
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
        details.put("mode", "SHARDINGSPHERE");
        details.put("ruleVersion", properties.getRuleVersion());
        String checksum = properties.getRuleChecksum();
        details.put("ruleChecksumPrefix", checksum.substring(0, Math.min(checksum.length(), 19)));
        details.put("compositeDataSourceActive", true);
        builder.withDetail("transactionSharding", details);
    }
}
