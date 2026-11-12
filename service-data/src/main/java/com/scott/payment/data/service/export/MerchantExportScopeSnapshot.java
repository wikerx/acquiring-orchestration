package com.scott.payment.data.service.export;

import lombok.Data;

/**
 * 商户端异步导出数据范围快照，只冻结认证商户号并与任务归属字段交叉校验。
 */
@Data
public class MerchantExportScopeSnapshot {
    private String merchantId;
}
