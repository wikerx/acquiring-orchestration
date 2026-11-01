package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表规则响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
 */
@Data
public class ShardingRuleResponse {

    /**
     * 监控治理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private String ruleKey;

    /**
     * 当前实例加载的交易分片规则版本。
     */
    private String ruleVersion;

    /**
     * 当前实例规则 checksum 前缀。
     */
    private String ruleChecksumPrefix;

    /**
     * 当前实例已验证并登记的季度节点。
     */
    private List<String> verifiedPhysicalNodes = new ArrayList<>();

    /**
     * 当前季度是否已登记为实际节点。
     */
    private Boolean currentNodeRegistered;

    /**
     * 下一季度是否已登记为实际节点。
     */
    private Boolean nextNodeRegistered;

    private String logicalTable;

    private String templateTable;

    private Boolean enabled;

    private String idColumn;

    private String shardingColumn;

    private String actualDataSource;

    private String description;

    private Integer startYear;

    private Integer startQuarter;

    private Integer endYear;

    private Integer endQuarter;

    private String tableNameFormat;

    private String currentPhysicalTable;

    private String nextPhysicalTable;

    private Integer physicalTableCount;

    private List<String> physicalTables = new ArrayList<>();
}
