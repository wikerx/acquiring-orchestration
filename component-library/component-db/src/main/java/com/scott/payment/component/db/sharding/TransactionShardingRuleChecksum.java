package com.scott.payment.component.db.sharding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingRuleChecksum
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 对不含连接凭证的交易分片规则执行稳定序列化和 SHA-256 校验，供五个直连服务比对规则一致性。
 * @status : create
 */
public final class TransactionShardingRuleChecksum {

    private TransactionShardingRuleChecksum() {
    }

    /**
     * 对影响路由结果的规则字段计算稳定 SHA-256，不纳入模式和服务名单等实例级字段。
     *
     * @param properties 待发布的交易分片规则
     * @return 带 sha256 前缀的十六进制校验和
     */
    public static String calculate(TransactionShardingProperties properties) {
        String canonical = canonicalize(properties);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String canonicalize(TransactionShardingProperties properties) {
        List<String> tables = properties.getLogicTables().stream().sorted().toList();
        List<String> nodes = properties.getPhysicalNodes().stream().sorted().toList();
        List<String> replicas = properties.getReplicaDataSources().stream().sorted(Comparator.naturalOrder()).toList();
        TransactionShardingProperties.QueryBudget budget = properties.getQueryBudget();
        return String.join("\n",
                "ruleVersion=" + properties.getRuleVersion(),
                "databaseZoneId=" + properties.getDatabaseZoneId(),
                "shardingColumn=" + properties.getShardingColumn(),
                "primaryDataSource=" + properties.getPrimaryDataSource(),
                "replicaDataSources=" + String.join(",", replicas),
                "physicalNodes=" + String.join(",", nodes),
                "logicTables=" + String.join(",", tables),
                "synchronousTimeoutMillis=" + budget.getSynchronousTimeoutMillis(),
                "maxResultRows=" + budget.getMaxResultRows(),
                "maxConcurrentExportsPerUser=" + budget.getMaxConcurrentExportsPerUser());
    }
}
