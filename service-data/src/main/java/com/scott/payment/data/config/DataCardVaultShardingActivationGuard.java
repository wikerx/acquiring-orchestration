package com.scott.payment.data.config;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataCardVaultShardingActivationGuard
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : 卡资料消费启动门禁，确保启用消费前交易分片规则已经接管卡资料逻辑表。
 * @status : create
 */
@Component
@ConditionalOnProperty(prefix = "data.card-vault", name = "enabled", havingValue = "true")
public class DataCardVaultShardingActivationGuard implements InitializingBean {

    private final TransactionShardingProperties shardingProperties;

    /**
     * 创建卡资料分片启动门禁。
     *
     * @param shardingProperties 当前服务绑定的交易分片规则
     */
    public DataCardVaultShardingActivationGuard(TransactionShardingProperties shardingProperties) {
        this.shardingProperties = shardingProperties;
    }

    /**
     * 在卡资料相关 Bean 投入使用前确认第 24 张逻辑表已发布。
     */
    @Override
    public void afterPropertiesSet() {
        if (!shardingProperties.getLogicTables().contains(TransactionShardingProperties.CARD_VAULT_LOGIC_TABLE)) {
            throw new IllegalStateException("data.card-vault requires transaction_card_vault in transaction sharding rules");
        }
    }
}
