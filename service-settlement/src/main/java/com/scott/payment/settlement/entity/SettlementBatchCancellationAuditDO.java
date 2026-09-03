package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchCancellationAuditDO
 * @date : 2026-08-31 18:00
 * @email : scott_x@163.com
 * @description : 正式结算批次取消的不可变业务审计快照，保存可信操作主体、幂等键和释放结果。
 * @status : create
 */
@Data
@TableName("settlement_batch_cancellation_audit")
public class SettlementBatchCancellationAuditDO {
    /** 取消审计数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 被取消的正式结算批次号，数据库必须唯一。 */
    private String settlementBatchNo;
    /** 取消请求幂等键，数据库必须唯一。 */
    private String requestKey;
    /** service-admin 页面读取的原批次 version。 */
    private Long expectedVersion;
    /** 被取消批次所属平台商户号。 */
    private String merchantId;
    /** 取消前批次权威状态。 */
    private String batchStatusBefore;
    /** 取消事务实际释放候选及关系数量。 */
    private Integer releasedCandidateCount;
    /** service-admin 可信注入的操作账户 ID。 */
    private Long operatorAccountId;
    /** 操作账户展示名。 */
    private String operatorAccountName;
    /** 操作时角色权限快照，不包含鉴权凭据。 */
    private String operatorRoleSnapshot;
    /** 操作客户端 IP 审计值。 */
    private String clientIp;
    /** 操作客户端 User-Agent 审计值。 */
    private String userAgent;
    /** 人工取消原因，不允许为空。 */
    private String reason;
    /** 管理员实际操作时间，数据库精度为毫秒。 */
    private LocalDateTime operationTime;
    /** 结算领域完成取消事务时间。 */
    private LocalDateTime cancelledTime;
    /** 审计快照创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
}
