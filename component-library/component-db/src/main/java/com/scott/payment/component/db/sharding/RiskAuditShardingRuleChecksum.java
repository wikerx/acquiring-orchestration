package com.scott.payment.component.db.sharding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 对风控审计分片路由字段执行稳定序列化和 SHA-256 校验，防止 Admin 与 Data 装载不同规则。
 */
public final class RiskAuditShardingRuleChecksum {

    /**
     * 禁止实例化风控审计规则校验工具类。
     */
    private RiskAuditShardingRuleChecksum() {
    }

    /**
     * 计算影响风控审计路由结果的稳定摘要，不纳入服务白名单等实例级字段。
     *
     * @param properties 待发布风控审计分片规则
     * @return 带 sha256 前缀的十六进制摘要
     */
    public static String calculate(RiskAuditShardingProperties properties) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(
                    digest.digest(canonicalize(properties).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 按固定字段顺序生成规范文本，集合先排序以消除 YAML 顺序差异。
     *
     * @param properties 待发布风控审计分片规则
     * @return 可重复计算摘要的规范文本
     */
    static String canonicalize(RiskAuditShardingProperties properties) {
        List<String> tables = properties.getLogicTables().stream().sorted().toList();
        List<String> nodes = properties.getPhysicalNodes().stream().sorted().toList();
        List<String> replicas = properties.getReplicaDataSources().stream().sorted().toList();
        return String.join("\n",
                "ruleVersion=" + properties.getRuleVersion(),
                "databaseZoneId=" + properties.getDatabaseZoneId(),
                "shardingColumn=" + properties.getShardingColumn(),
                "primaryDataSource=" + properties.getPrimaryDataSource(),
                "replicaDataSources=" + String.join(",", replicas),
                "physicalNodes=" + String.join(",", nodes),
                "logicTables=" + String.join(",", tables));
    }
}
