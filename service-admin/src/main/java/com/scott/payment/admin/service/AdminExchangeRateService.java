package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateBatchSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.GenerateBusinessRateRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotResponse;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * 管理后台汇率管理服务。
 *
 * <p>定义汇率源、原始汇率、汇率规则、业务汇率和使用快照的管理端业务边界。汇率源币种映射只供任务解析外部源名称，
 * 不作为管理端独立 CRUD 能力暴露。</p>
 */
public interface AdminExchangeRateService {

    /**
     * 分页查询汇率源配置。
     *
     * @param query 查询条件，允许为空
     * @return 汇率源分页结果
     */
    PageResult<SourceResponse> pageSources(SourceQuery query);

    /**
     * 按条件查询汇率源配置列表，用于统一 Excel 导出。
     *
     * @param query 查询条件，允许为空
     * @return 汇率源列表
     */
    List<SourceResponse> listSources(SourceQuery query);

    /**
     * 查询汇率源详情。
     *
     * @param id 汇率源主键
     * @return 汇率源详情
     */
    SourceResponse getSource(Long id);

    /**
     * 新增汇率源配置。
     *
     * @param request 保存请求
     * @return 新增后的汇率源详情
     */
    SourceResponse createSource(SourceSaveRequest request);

    /**
     * 修改汇率源配置。
     *
     * @param id      汇率源主键
     * @param request 保存请求
     * @return 修改后的汇率源详情
     */
    SourceResponse updateSource(Long id, SourceSaveRequest request);

    /**
     * 启用或停用汇率源。
     *
     * @param id     汇率源主键
     * @param status 状态值，1 表示启用，0 表示停用
     * @return 切换状态后的汇率源详情
     */
    SourceResponse updateSourceStatus(Long id, Integer status);

    /**
     * 删除未被原始汇率、规则或业务汇率引用的汇率源。
     *
     * @param id 汇率源主键
     */
    void deleteSource(Long id);

    /**
     * 分页查询原始汇率记录。
     *
     * @param query 查询条件，允许为空
     * @return 原始汇率分页结果
     */
    PageResult<RawRateResponse> pageRawRates(RawRateQuery query);

    /**
     * 按条件查询原始汇率列表，用于统一 Excel 导出。
     *
     * @param query 查询条件，允许为空
     * @return 原始汇率列表
     */
    List<RawRateResponse> listRawRates(RawRateQuery query);

    /**
     * 查询原始汇率详情。
     *
     * @param id 原始汇率主键
     * @return 原始汇率详情
     */
    RawRateResponse getRawRate(Long id);

    /**
     * 手工新增原始汇率记录。
     *
     * @param request 原始汇率保存请求
     * @return 新增后的原始汇率详情
     */
    RawRateResponse createManualRawRate(RawRateSaveRequest request);

    /**
     * 作废未生成业务汇率的原始汇率。
     *
     * @param id         原始汇率主键
     * @param voidReason 作废原因
     * @return 作废后的原始汇率详情
     */
    RawRateResponse voidRawRate(Long id, String voidReason);

    /**
     * 分页查询汇率规则。
     *
     * @param query 查询条件，允许为空
     * @return 汇率规则分页结果
     */
    PageResult<RuleResponse> pageRules(RuleQuery query);

    /**
     * 按条件查询汇率规则列表，用于统一 Excel 导出。
     *
     * @param query 查询条件，允许为空
     * @return 汇率规则列表
     */
    List<RuleResponse> listRules(RuleQuery query);

    /**
     * 查询汇率规则详情。
     *
     * @param id 规则主键
     * @return 汇率规则详情
     */
    RuleResponse getRule(Long id);

    /**
     * 新增汇率规则。
     *
     * @param request 规则保存请求
     * @return 新增后的规则详情
     */
    RuleResponse createRule(RuleSaveRequest request);

    /**
     * 修改汇率规则。
     *
     * @param id      规则主键
     * @param request 规则保存请求
     * @return 修改后的规则详情
     */
    RuleResponse updateRule(Long id, RuleSaveRequest request);

    /**
     * 启用或停用汇率规则。
     *
     * @param id     规则主键
     * @param status 状态值，1 表示启用，0 表示停用
     * @return 切换状态后的规则详情
     */
    RuleResponse updateRuleStatus(Long id, Integer status);

    /**
     * 分页查询业务汇率。
     *
     * @param query 查询条件，允许为空
     * @return 业务汇率分页结果
     */
    PageResult<BusinessRateResponse> pageBusinessRates(BusinessRateQuery query);

    /**
     * 按条件查询业务汇率列表，用于统一 Excel 导出。
     *
     * @param query 查询条件，允许为空
     * @return 业务汇率列表
     */
    List<BusinessRateResponse> listBusinessRates(BusinessRateQuery query);

    /**
     * 查询业务汇率详情。
     *
     * @param id 业务汇率主键
     * @return 业务汇率详情
     */
    BusinessRateResponse getBusinessRate(Long id);

    /**
     * 手工新增可直接使用的业务汇率。
     *
     * @param request 业务汇率保存请求
     * @return 新增后的业务汇率
     */
    BusinessRateResponse createManualBusinessRate(BusinessRateSaveRequest request);

    /**
     * 批量手工新增可直接使用的业务汇率。
     *
     * @param request 批量保存请求
     * @return 新增后的业务汇率列表
     */
    List<BusinessRateResponse> createManualBusinessRates(BusinessRateBatchSaveRequest request);

    /**
     * 根据原始汇率和规则生成最终业务汇率，并使同范围旧业务汇率失效。
     *
     * @param request 业务汇率生成请求
     * @return 生成后的业务汇率详情
     */
    BusinessRateResponse generateBusinessRate(GenerateBusinessRateRequest request);

    /**
     * 启用或停用业务汇率。
     *
     * @param id     业务汇率主键
     * @param status 状态值，1 表示启用，0 表示停用
     * @return 切换状态后的业务汇率详情
     */
    BusinessRateResponse updateBusinessRateStatus(Long id, Integer status);

    /**
     * 分页查询汇率使用快照。
     *
     * @param query 查询条件，允许为空
     * @return 使用快照分页结果
     */
    PageResult<UsageSnapshotResponse> pageUsageSnapshots(UsageSnapshotQuery query);

    /**
     * 按条件查询汇率使用快照列表，用于统一 Excel 导出。
     *
     * @param query 查询条件，允许为空
     * @return 使用快照列表
     */
    List<UsageSnapshotResponse> listUsageSnapshots(UsageSnapshotQuery query);

    /**
     * 查询汇率使用快照详情。
     *
     * @param id 快照主键
     * @return 使用快照详情
     */
    UsageSnapshotResponse getUsageSnapshot(Long id);
}
