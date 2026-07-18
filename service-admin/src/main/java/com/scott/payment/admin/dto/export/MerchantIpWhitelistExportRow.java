package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIpWhitelistExportRow
 * @date : 2026-07-18
 * @email : scott_x@163.com
 * @description : 商户 IP 白名单导出行对象，位于 service-admin 导出传输层，按商户维度导出白名单开关和精确 IP 集合。
 * @status : create
 */
@Data
public class MerchantIpWhitelistExportRow {

    /**
     * 商户号。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.merchantIpWhitelist.merchantId", width = 18)
    private String merchantId;

    /**
     * 商户主体名称。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.merchantIpWhitelist.merchantName", width = 28)
    private String merchantName;

    /**
     * 商户简称。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.merchantIpWhitelist.merchantShortName", width = 22)
    private String merchantShortName;

    /**
     * 商户维度白名单校验开关文案。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.merchantIpWhitelist.accessControl", width = 16)
    private String accessControl;

    /**
     * 当前查询条件命中的 IP 白名单集合。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.merchantIpWhitelist.ipWhitelists", width = 56)
    private String ipWhitelists;

    /**
     * 最近更新人。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.merchantIpWhitelist.updateBy", width = 18)
    private String updateBy;

    /**
     * 最近更新时间。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.merchantIpWhitelist.updateTime", width = 22)
    private LocalDateTime updateTime;

    /**
     * 商户开关备注。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.merchantIpWhitelist.configRemark", width = 32)
    private String configRemark;
}
