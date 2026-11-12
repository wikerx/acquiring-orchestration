package com.scott.payment.data.client.admin;

import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotPageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.SourcePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.SourcePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RulePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RulePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityPageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertRulePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertRulePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertEventPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertEventPageResponse;

/**
 * service-data 读取管理端汇率事实导出来源的内部客户端。
 */
public interface AdminExchangeExportSourceClient {

    /**
     * 分页读取原始汇率事实。
     *
     * @param request 冻结查询和服务端分页
     * @return 原始汇率事实页
     */
    RawRatePageResponse loadRawRatePage(RawRatePageRequest request);

    /**
     * 分页读取业务汇率事实。
     *
     * @param request 冻结查询和服务端分页
     * @return 业务汇率事实页
     */
    BusinessRatePageResponse loadBusinessRatePage(BusinessRatePageRequest request);

    /**
     * 分页读取汇率使用快照事实。
     *
     * @param request 冻结查询和服务端分页
     * @return 汇率使用快照事实页
     */
    UsageSnapshotPageResponse loadUsageSnapshotPage(UsageSnapshotPageRequest request);

    /**
     * 按任务创建时冻结的筛选条件分页读取汇率源配置。
     *
     * @param request 操作人身份、冻结查询和服务端分页参数
     * @return 汇率源配置页
     */
    SourcePageResponse loadSourcePage(SourcePageRequest request);

    /**
     * 按任务创建时冻结的筛选条件分页读取汇率规则配置。
     *
     * @param request 操作人身份、冻结查询和服务端分页参数
     * @return 汇率规则配置页
     */
    RulePageResponse loadRulePage(RulePageRequest request);

    /**
     * 按任务创建时冻结的筛选条件分页读取权威汇率波动摘要。
     *
     * @param request 操作人身份、冻结查询和服务端分页参数
     * @return 汇率波动摘要页
     */
    VolatilityPageResponse loadVolatilityPage(VolatilityPageRequest request);

    /**
     * 按任务创建时冻结的筛选条件分页读取汇率预警规则。
     *
     * @param request 操作人身份、冻结查询和服务端分页参数
     * @return 汇率预警规则页
     */
    AlertRulePageResponse loadAlertRulePage(AlertRulePageRequest request);

    /**
     * 按任务创建时冻结的筛选条件分页读取汇率预警事件。
     *
     * @param request 操作人身份、冻结查询和服务端分页参数
     * @return 汇率预警事件页
     */
    AlertEventPageResponse loadAlertEventPage(AlertEventPageRequest request);
}
