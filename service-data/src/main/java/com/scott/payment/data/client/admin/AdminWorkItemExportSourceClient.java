package com.scott.payment.data.client.admin;

import com.scott.payment.data.client.admin.dto.AdminWorkItemExportSourceDTOs.PageRequest;
import com.scott.payment.data.client.admin.dto.AdminWorkItemExportSourceDTOs.PageResponse;

/**
 * service-data 分页读取管理端统一待办导出源的内部客户端契约。
 */
public interface AdminWorkItemExportSourceClient {

    /**
     * 按冻结的数据范围读取一页运营摘要。
     */
    PageResponse loadPage(PageRequest request);
}
