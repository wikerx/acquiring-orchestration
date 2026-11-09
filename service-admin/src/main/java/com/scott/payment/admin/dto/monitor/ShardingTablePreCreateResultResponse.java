package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表预创建批次响应，汇总试运行标识、时区和各物理表处理结果。
 */
@Data
public class ShardingTablePreCreateResultResponse {

    private Boolean dryRun;

    /**
     * 时区配置，使用 IANA 时区标识解释关联的本地日期时间。
     */
    private String timezone;

    private String strategy;

    private String currentQuarter;

    /**
     * 待人工评审的候选规则版本。
     */
    private String candidateRuleVersion;

    /**
     * 候选规则 SHA-256 checksum。
     */
    private String candidateRuleChecksum;

    /**
     * 仅由已建表且全量校验通过的季度组成的候选节点。
     */
    private List<String> verifiedPhysicalNodes = new ArrayList<>();

    /**
     * 是否允许进入人工发布和滚动重启步骤。
     */
    private Boolean publicationReady = Boolean.FALSE;

    /**
     * 阻止候选规则发布的治理原因。
     */
    private List<String> publicationBlockers = new ArrayList<>();

    /**
     * 风控审计表族待人工评审的候选规则版本。
     */
    private String riskAuditCandidateRuleVersion;

    /**
     * 风控审计候选规则 SHA-256 checksum。
     */
    private String riskAuditCandidateRuleChecksum;

    /**
     * 风控审计表族已验证季度节点。
     */
    private List<String> riskAuditVerifiedPhysicalNodes = new ArrayList<>();

    /**
     * 风控审计表族是否具备人工发布条件。
     */
    private Boolean riskAuditPublicationReady = Boolean.FALSE;

    /**
     * 阻止风控审计候选规则发布的原因。
     */
    private List<String> riskAuditPublicationBlockers = new ArrayList<>();

    /**
     * 下一步受控操作标识，不会由接口自动执行。
     */
    private String nextAction;

    private List<String> targetQuarters = new ArrayList<>();

    private List<String> createdTables = new ArrayList<>();

    private List<String> skippedTables = new ArrayList<>();

    private List<String> failedTables = new ArrayList<>();

    private List<String> schemaMismatchTables = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();

    private List<ShardingTablePreCreateTableResultResponse> tableResults = new ArrayList<>();
}
