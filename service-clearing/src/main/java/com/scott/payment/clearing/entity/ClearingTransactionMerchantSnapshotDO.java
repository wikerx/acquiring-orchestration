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

    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotId;
    private String transactionId;
    private String operationId;
    private String merchantId;
    private String feeConfigSnapshotJson;
    private Long feePlanId;
    private Long feePlanVersionId;
    private Integer feePlanVersionNo;
    private String feeSnapshotHash;
    private LocalDateTime feeSnapshotTime;
    private LocalDateTime transactionDateTime;
}
