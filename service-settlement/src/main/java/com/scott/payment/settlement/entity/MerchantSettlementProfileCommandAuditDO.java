package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结算档案创建和版本切换的不可变审计；请求键同时承担数据库级幂等保护。
 */
@Data
@TableName("merchant_settlement_profile_command_audit")
public class MerchantSettlementProfileCommandAuditDO {

    /**
     * 审计记录数据库主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * service-admin 生成的全局请求幂等键。
     */
    private String requestKey;
    /**
     * 规范化命令的 SHA-256 指纹，用于拒绝同键异参。
     */
    private String requestFingerprint;
    /**
     * CREATE 或 CREATE_VERSION。
     */
    private String commandAction;
    /**
     * 命令作用的平台商户号。
     */
    private String merchantId;
    /**
     * 版本切换前的档案号；初始创建时为空。
     */
    private String sourceProfileNo;
    /**
     * 命令生成的活动档案号。
     */
    private String resultProfileNo;
    /**
     * 页面读取到的源档案版本；初始创建时为空。
     */
    private Long expectedVersion;
    /**
     * 可信管理账号 ID。
     */
    private Long operatorAccountId;
    /**
     * 管理账号显示名称快照。
     */
    private String operatorAccountName;
    /**
     * 操作时角色权限快照，不包含鉴权凭据。
     */
    private String operatorRoleSnapshot;
    /**
     * 操作客户端 IP 审计值。
     */
    private String clientIp;
    /**
     * 操作客户端 User-Agent 审计值。
     */
    private String userAgent;
    /**
     * 人工配置或变更原因。
     */
    private String reason;
    /**
     * 管理端记录的实际操作时间。
     */
    private LocalDateTime operationTime;
    /**
     * 结算领域提交命令的时间。
     */
    private LocalDateTime createTime;
}
