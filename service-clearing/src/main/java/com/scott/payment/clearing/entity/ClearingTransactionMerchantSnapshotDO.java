package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionMerchantSnapshotDO
 * @date : 2026-08-26 09:55
 * @email : scott_x@163.com
 * @description : 清分服务对动作商户快照的只读投影，只读取费用版本身份和不含持卡人数据的冻结费用 JSON。
 * @status : create
 */
@Data
@TableName("transaction_merchant_snapshot")
public class ClearingTransactionMerchantSnapshotDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 快照表自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 动作商户快照业务号。 */
    private String snapshotId;
    /** 动作交易号。 */
    private String transactionId;
    /** 动作操作号。 */
    private String operationId;
    /** 平台商户号。 */
    private String merchantId;
    /** 动作受理时冻结的费用配置 JSON，不包含持卡人或密钥信息。 */
    private String feeConfigSnapshotJson;
    /** 冻结费用方案 ID。 */
    private Long feePlanId;
    /** 冻结不可变费用方案版本 ID。 */
    private Long feePlanVersionId;
    /** 冻结费用版本号。 */
    private Integer feePlanVersionNo;
    /** 规范化费用快照 SHA-256。 */
    private String feeSnapshotHash;
    /** 费用配置冻结 UTC 时间。 */
    private LocalDateTime feeSnapshotTime;
    /** 动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
}
