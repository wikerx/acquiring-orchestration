package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端商户 IP 白名单 Excel 的独立行模型。
 *
 * <p>一行保持 Admin 既有的按商户聚合语义，范围由任务创建时的可信快照限定。</p>
 */
@Data
public class MerchantIpWhitelistExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.merchantIpWhitelist.merchantId", width = 18)
    private String merchantId;
    @ExcelExportColumn(order = 2, headerKey = "excel.merchantIpWhitelist.merchantName", width = 28)
    private String merchantName;
    @ExcelExportColumn(order = 3, headerKey = "excel.merchantIpWhitelist.merchantShortName", width = 22)
    private String merchantShortName;
    @ExcelExportColumn(order = 4, headerKey = "excel.merchantIpWhitelist.ipWhitelists", width = 56)
    private String ipWhitelists;
    @ExcelExportColumn(order = 5, headerKey = "excel.merchantIpWhitelist.updateBy", width = 18)
    private String updateBy;
    @ExcelExportColumn(order = 6, headerKey = "excel.merchantIpWhitelist.updateTime", width = 22)
    private LocalDateTime updateTime;
}
