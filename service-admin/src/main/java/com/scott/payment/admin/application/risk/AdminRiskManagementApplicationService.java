package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.admin.mapper.RiskManagementMapper;
import com.scott.payment.admin.support.risk.RiskFunctionDefinition;
import com.scott.payment.admin.support.risk.RiskListValueNormalizer;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRiskManagementApplicationService
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 收单风控管理应用服务，负责管理端名单、规则、交易加黑和风控记录的页面编排，不参与实时交易风控决策。
 * @status : create
 */
@Service
public class AdminRiskManagementApplicationService {

    private static final String DEFAULT_SCOPE = "GLOBAL";
    private static final String DEFAULT_RISK_LEVEL = "MEDIUM";
    private static final String DEFAULT_DECISION_ACTION = "REVIEW";
    private static final String VALIDITY_SUPER_LONG = "SUPER_LONG";
    private static final String VALIDITY_LONG = "LONG";
    private static final String VALIDITY_LIMITED = "LIMITED";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_IMPORT = "IMPORT";
    private static final int ENABLED = 1;
    private static final int LONG_VALIDITY_MIN_DAYS = 120;

    private final RiskManagementMapper riskManagementMapper;
    private final RiskListValueNormalizer riskListValueNormalizer;

    public AdminRiskManagementApplicationService(RiskManagementMapper riskManagementMapper,
                                                 RiskListValueNormalizer riskListValueNormalizer) {
        this.riskManagementMapper = riskManagementMapper;
        this.riskListValueNormalizer = riskListValueNormalizer;
    }

    /**
     * 查询全部风险功能定义。
     *
     * @return 功能定义列表
     */
    public List<RiskDTOs.FunctionDefinitionResponse> functions() {
        return RiskFunctionDefinition.all().stream().map(this::toDefinitionResponse).toList();
    }

    /**
     * 查询页面下拉选项。
     *
     * @return 页面下拉选项
     */
    public RiskDTOs.RiskOptionsResponse options() {
        RiskDTOs.RiskOptionsResponse response = new RiskDTOs.RiskOptionsResponse();
        response.setStatusOptions(List.of(option("启用", "1", "success"), option("停用", "0", "info")));
        response.setMerchantScopeOptions(List.of(option("全局风控", "GLOBAL", null), option("商户风控", "MERCHANT", null)));
        response.setRiskLevelOptions(List.of(
                option("低风险", "LOW", "success"),
                option("中风险", "MEDIUM", "warning"),
                option("高风险", "HIGH", "danger"),
                option("严重风险", "CRITICAL", "danger")
        ));
        response.setDecisionActionOptions(List.of(
                option("通过", "PASS", "success"),
                option("拒绝", "REJECT", "danger"),
                option("人工复核", "REVIEW", "warning")
        ));
        response.setCardBrandOptions(toOptions(riskManagementMapper.selectDictOptions("card_brand", "zh-CN")));
        response.setLimitTypeOptions(toOptions(riskManagementMapper.selectDictOptions("channel_limit_type", "zh-CN")));
        response.setCountryOptions(toOptions(riskManagementMapper.selectCountryOptions()));
        response.setCurrencyOptions(toOptions(riskManagementMapper.selectCurrencyOptions()));
        response.setValidityTypeOptions(List.of(
                option("超长期", VALIDITY_SUPER_LONG, "success"),
                option("长期", VALIDITY_LONG, "warning"),
                option("限定有效期", VALIDITY_LIMITED, "info")
        ));
        response.setSourceTypeOptions(List.of(
                option("手工录入", "MANUAL", "primary"),
                option("批量导入", "IMPORT", "warning"),
                option("系统生成", "SYSTEM", "info")
        ));
        return response;
    }

    /**
     * 分页查询 AML、黑名单或白名单配置列表。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param request      查询条件，允许为空，为空时使用默认分页
     * @return 名单分页数据，响应值仅返回脱敏展示字段和配置字段
     */
    public PageResult<RiskDTOs.RiskRecordResponse> pageList(String moduleType, String functionCode, RiskDTOs.RiskListQueryRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "list");
        RiskDTOs.RiskListQueryRequest query = request == null ? new RiskDTOs.RiskListQueryRequest() : request;
        if (definition.isRegionFunction()) {
            String countryAlpha3 = countryAlpha3FromAlpha2(query.getCountryAlpha2());
            long total = riskManagementMapper.countRegion(query.getMerchantScope(), query.getMerchantId(), query.getMatchValue(), countryAlpha3, query.getStatus());
            List<RiskDTOs.RiskRecordResponse> rows = riskManagementMapper.selectRegionPage(
                    query.getMerchantScope(),
                    query.getMerchantId(),
                    query.getMatchValue(),
                    countryAlpha3,
                    query.getStatus(),
                    offset(query.safePageNo(), query.safePageSize()),
                    query.safePageSize()
            ).stream().map(this::toRecordResponse).toList();
            return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
        }
        String cardBinLookupNumber = cardBinLookupNumber(definition, query.getMatchValue());
        String countryAlpha3 = hasCountryFields(definition) ? countryAlpha3FromAlpha2(query.getCountryAlpha2()) : query.getCountryAlpha2();
        long total = riskManagementMapper.countList(definition.getTableName(), query.getMerchantScope(), query.getMerchantId(), query.getMatchValue(), cardBinLookupNumber, countryAlpha3, query.getStatus(), hasCountryFields(definition));
        List<RiskDTOs.RiskRecordResponse> rows = riskManagementMapper.selectListPage(
                definition.getTableName(),
                query.getMerchantScope(),
                query.getMerchantId(),
                query.getMatchValue(),
                cardBinLookupNumber,
                countryAlpha3,
                query.getStatus(),
                offset(query.safePageNo(), query.safePageSize()),
                query.safePageSize(),
                hasCountryFields(definition)
        ).stream().map(this::toRecordResponse).toList();
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 查询 AML、黑名单或白名单配置详情。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     * @return 名单配置详情
     */
    public RiskDTOs.RiskRecordResponse listDetail(String moduleType, String functionCode, Long id) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "detail");
        return toRecordResponse(requireRecord(definition.getTableName(), id));
    }

    /**
     * 查询名单编辑详情。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     * @return 编辑详情，敏感明文仅在该接口授权后返回
     */
    public RiskDTOs.RiskRecordResponse listEditDetail(String moduleType, String functionCode, Long id) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "edit");
        Map<String, Object> record = requireRecord(definition.getTableName(), id);
        RiskDTOs.RiskRecordResponse response = toRecordResponse(record);
        String cipherText = asString(record.get("match_value_cipher"));
        response.setMatchValuePlain(StringUtils.hasText(cipherText)
                ? riskListValueNormalizer.decryptPlain(cipherText)
                : asString(record.get("match_value_masked")));
        return response;
    }

    /**
     * 新增 AML、黑名单或白名单配置。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param request      新增请求，敏感元素必须由调用方传入脱敏值或哈希值
     * @return 新增后的配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse createList(String moduleType, String functionCode, RiskDTOs.RiskListSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "add");
        List<RiskDTOs.RiskListSaveRequest> requests = expandCountryListRequests(definition, request);
        Map<String, Object> lastData = null;
        for (RiskDTOs.RiskListSaveRequest itemRequest : requests) {
            Map<String, Object> data = listData(definition, itemRequest, SOURCE_MANUAL);
            ensureListNotDuplicated(definition, null, data);
            int rows = riskManagementMapper.insertListRecord(definition.getTableName(), data, currentOperatorName(), hasRangeFields(definition), hasCardBrandField(definition), hasCountryFields(definition), hasCountryNumericField(definition));
            if (rows != 1) {
                throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "create risk list failed");
            }
            writeChange(definition, null, "CREATE", null, data);
            lastData = data;
        }
        return latestListRecord(definition, lastData == null ? Map.of() : lastData);
    }

    /**
     * 修改 AML、黑名单或白名单配置。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     * @param request      修改请求，敏感元素必须由调用方传入脱敏值或哈希值
     * @return 修改后的配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse updateList(String moduleType, String functionCode, Long id, RiskDTOs.RiskListSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "edit");
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        Map<String, Object> data = listData(definition, request, SOURCE_MANUAL);
        ensureListNotDuplicated(definition, id, data);
        int rows = riskManagementMapper.updateListRecord(definition.getTableName(), id, data, currentOperatorName(), hasRangeFields(definition), hasCardBrandField(definition), hasCountryFields(definition), hasCountryNumericField(definition));
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "risk record not found");
        }
        writeChange(definition, id, "UPDATE", before, data);
        return listDetail(moduleType, functionCode, id);
    }

    /**
     * 新增高风险区域黑名单配置。
     *
     * @param request 区域保存请求，支持国家、州省、城市三级区域粒度
     * @return 新增后的区域配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse createRegion(RiskDTOs.RegionSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require("BLACK", "region");
        ensureFunctionPermission(definition, "add");
        List<String> countryCodes = regionCreateCountryCodes(request);
        Map<String, Object> lastData = null;
        for (String countryCode : countryCodes) {
            request.setCountryAlpha2(countryCode);
            Map<String, Object> data = regionData(request, SOURCE_MANUAL);
            ensureRegionNotDuplicated(null, data);
            int rows = riskManagementMapper.insertRegion(data, currentOperatorName());
            if (rows != 1) {
                throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "create region failed");
            }
            writeChange(definition, null, "CREATE", null, data);
            lastData = data;
        }
        return latestListRecord(definition, lastData);
    }

    /**
     * 修改高风险区域黑名单配置。
     *
     * @param id      区域配置记录ID
     * @param request 区域保存请求，支持国家、州省、城市三级区域粒度
     * @return 修改后的区域配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse updateRegion(Long id, RiskDTOs.RegionSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require("BLACK", "region");
        ensureFunctionPermission(definition, "edit");
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        Map<String, Object> data = regionData(request, SOURCE_MANUAL);
        ensureRegionNotDuplicated(id, data);
        int rows = riskManagementMapper.updateRegion(id, data, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "region record not found");
        }
        writeChange(definition, id, "UPDATE", before, data);
        return listDetail("BLACK", "region", id);
    }

    /**
     * 删除名单或规则配置，采用软删除并记录配置变更日志。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(String moduleType, String functionCode, Long id) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "remove");
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        int rows = riskManagementMapper.softDelete(definition.getTableName(), id, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "risk record not found");
        }
        writeChange(definition, id, "DELETE", before, null);
    }

    /**
     * 批量删除名单或规则配置，逐条软删除并记录配置变更日志。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param request      批量删除请求，ID 列表不能为空
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchRemove(String moduleType, String functionCode, RiskDTOs.BatchRemoveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "remove");
        List<Long> ids = request == null || request.getIds() == null
                ? List.of()
                : request.getIds().stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择需要删除的记录");
        }
        String operator = currentOperatorName();
        for (Long id : ids) {
            Map<String, Object> before = requireRecord(definition.getTableName(), id);
            int rows = riskManagementMapper.softDelete(definition.getTableName(), id, operator);
            if (rows != 1) {
                throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "risk record not found");
            }
            writeChange(definition, id, "DELETE", before, null);
        }
    }

    /**
     * 更新名单或规则状态，状态值只允许启用或停用。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     * @param request      状态更新请求
     * @return 更新后的配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse updateStatus(String moduleType, String functionCode, Long id, RiskDTOs.StatusUpdateRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "status");
        Integer status = request == null ? null : request.getStatus();
        if (status == null || (status != 0 && status != 1)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "invalid status");
        }
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        int rows = riskManagementMapper.updateStatus(definition.getTableName(), id, status, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "risk record not found");
        }
        writeChange(definition, id, "STATUS", before, Map.of("status", status));
        return listDetail(moduleType, functionCode, id);
    }

    /**
     * 分页查询内风控规则配置。
     *
     * @param functionCode 规则功能编码，用于解析物理表白名单和功能级权限
     * @param request      查询条件，允许为空，为空时使用默认分页
     * @return 规则配置分页数据
     */
    public PageResult<RiskDTOs.RiskRecordResponse> pageRules(String functionCode, RiskDTOs.RiskRuleQueryRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require("RULE", functionCode);
        ensureFunctionPermission(definition, "list");
        RiskDTOs.RiskRuleQueryRequest query = request == null ? new RiskDTOs.RiskRuleQueryRequest() : request;
        long total = riskManagementMapper.countRules(definition.getTableName(), query.getMerchantId(), query.getRuleName(), query.getMatchValue(), query.getCurrency(), query.getStatus());
        List<RiskDTOs.RiskRecordResponse> rows = riskManagementMapper.selectRulePage(
                definition.getTableName(),
                query.getMerchantId(),
                query.getRuleName(),
                query.getMatchValue(),
                query.getCurrency(),
                query.getStatus(),
                offset(query.safePageNo(), query.safePageSize()),
                query.safePageSize()
        ).stream().map(this::toRecordResponse).toList();
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 查询内风控规则详情。
     *
     * @param functionCode 规则功能编码，用于解析物理表白名单和功能级权限
     * @param id           规则记录ID
     * @return 规则详情
     */
    public RiskDTOs.RiskRecordResponse ruleDetail(String functionCode, Long id) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require("RULE", functionCode);
        ensureFunctionPermission(definition, "detail");
        return toRecordResponse(requireRecord(definition.getTableName(), id));
    }

    /**
     * 新增内风控规则配置。
     *
     * @param functionCode 规则功能编码，用于解析物理表白名单和功能级权限
     * @param request      规则保存请求，金额字段使用 BigDecimal
     * @return 新增后的规则配置
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse createRule(String functionCode, RiskDTOs.RiskRuleSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require("RULE", functionCode);
        ensureFunctionPermission(definition, "add");
        Map<String, Object> data = ruleData(request);
        int rows = riskManagementMapper.insertRule(definition.getTableName(), data, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "create risk rule failed");
        }
        writeChange(definition, null, "CREATE", null, data);
        return latestListRecord(definition, data);
    }

    /**
     * 修改内风控规则配置。
     *
     * @param functionCode 规则功能编码，用于解析物理表白名单和功能级权限
     * @param id           规则记录ID
     * @param request      规则保存请求，金额字段使用 BigDecimal
     * @return 修改后的规则配置
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse updateRule(String functionCode, Long id, RiskDTOs.RiskRuleSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require("RULE", functionCode);
        ensureFunctionPermission(definition, "edit");
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        Map<String, Object> data = ruleData(request);
        int rows = riskManagementMapper.updateRule(definition.getTableName(), id, data, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "risk rule not found");
        }
        writeChange(definition, id, "UPDATE", before, data);
        return ruleDetail(functionCode, id);
    }

    /**
     * 查询风控工作台概览。
     *
     * @return 各功能配置数量、启用数量和最近配置变更
     */
    public Map<String, Object> dashboard() {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (RiskFunctionDefinition definition : RiskFunctionDefinition.all()) {
            Map<String, Object> stats = riskManagementMapper.selectDashboardStats(definition.getTableName());
            long total = asLong(stats.get("total"));
            long enabled = asLong(stats.get("enabled"));
            long disabled = Math.max(total - enabled, 0);
            Map<String, Object> latestChange = riskManagementMapper.selectLatestChangeLog(definition.getModuleType(), definition.getFunctionCode());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("moduleType", definition.getModuleType());
            item.put("functionCode", definition.getFunctionCode());
            item.put("functionName", definition.getFunctionName());
            item.put("routePath", definition.getRoutePath());
            item.put("permissionPrefix", definition.getPermissionPrefix());
            item.put("regionFunction", definition.isRegionFunction());
            item.put("ruleFunction", definition.isRuleFunction());
            item.put("total", total);
            item.put("enabled", enabled);
            item.put("disabled", disabled);
            item.put("enabledRate", total == 0 ? 0 : Math.round(enabled * 100.0D / total));
            item.put("configured", total > 0);
            item.put("latestUpdateTime", stats.get("latest_update_time"));
            if (latestChange != null) {
                item.put("latestOperationType", latestChange.get("operation_type"));
                item.put("latestOperator", latestChange.get("operator"));
                item.put("latestOperationTime", latestChange.get("operation_time"));
            }
            groups.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("functions", groups);
        result.put("changeLogs", riskManagementMapper.selectChangeLogs(0, 10));
        return result;
    }

    /**
     * 查询今日风险事件，供风险工作台独立页面展示。
     *
     * @return 当日风控评估记录，按决策时间倒序
     */
    public List<Map<String, Object>> todayRiskEvents() {
        return riskManagementMapper.selectTodayRiskEvents(100);
    }

    /**
     * 查询高风险商户排行，供风险工作台独立页面展示。
     *
     * @return 近 30 天商户风险统计，按高风险命中数倒序
     */
    public List<Map<String, Object>> merchantRiskRanking() {
        return riskManagementMapper.selectMerchantRiskRanking(20);
    }

    /**
     * 分页查询配置变更日志。
     *
     * @param request 分页请求，允许为空，为空时使用默认分页
     * @return 配置变更日志分页数据
     */
    public PageResult<Map<String, Object>> pageChangeLogs(PageRequestAdapter request) {
        PageRequestAdapter query = request == null ? new PageRequestAdapter() : request;
        long total = riskManagementMapper.countChangeLogs();
        List<Map<String, Object>> rows = riskManagementMapper.selectChangeLogs(offset(query.safePageNo(), query.safePageSize()), query.safePageSize());
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 分页查询风控评估记录。
     *
     * @param request 查询条件，允许按商户号、商户订单号、平台订单号和决策结果过滤
     * @return 风控评估记录分页数据
     */
    public PageResult<Map<String, Object>> pageEvaluations(RiskDTOs.EvaluationQueryRequest request) {
        RiskDTOs.EvaluationQueryRequest query = request == null ? new RiskDTOs.EvaluationQueryRequest() : request;
        long total = riskManagementMapper.countEvaluations(query.getMerchantId(), query.getMerchantOrderNo(), query.getPaymentOrderNo(), query.getDecisionResult());
        List<Map<String, Object>> rows = riskManagementMapper.selectEvaluations(
                query.getMerchantId(),
                query.getMerchantOrderNo(),
                query.getPaymentOrderNo(),
                query.getDecisionResult(),
                offset(query.safePageNo(), query.safePageSize()),
                query.safePageSize()
        );
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 查询单笔风控评估命中明细。
     *
     * @param riskRecordNo 风控记录号
     * @return 命中明细列表
     */
    public List<Map<String, Object>> evaluationHits(String riskRecordNo) {
        if (!StringUtils.hasText(riskRecordNo)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "riskRecordNo is required");
        }
        return riskManagementMapper.selectEvaluationHits(riskRecordNo);
    }

    /**
     * 分页查询系统交易加黑记录。
     *
     * @param request 查询条件，允许按商户、订单号、加黑对象类型和状态过滤
     * @return 系统交易加黑分页数据
     */
    public PageResult<Map<String, Object>> pageTradeBlack(RiskDTOs.TradeBlackQueryRequest request) {
        RiskDTOs.TradeBlackQueryRequest query = request == null ? new RiskDTOs.TradeBlackQueryRequest() : request;
        long total = riskManagementMapper.countTradeBlack(query.getMerchantId(), query.getMerchantOrderNo(), query.getPaymentOrderNo(), query.getBlackTargetType(), query.getStatus());
        List<Map<String, Object>> rows = riskManagementMapper.selectTradeBlack(
                query.getMerchantId(),
                query.getMerchantOrderNo(),
                query.getPaymentOrderNo(),
                query.getBlackTargetType(),
                query.getStatus(),
                offset(query.safePageNo(), query.safePageSize()),
                query.safePageSize()
        );
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 新增系统交易加黑记录。
     *
     * @param request 保存请求，敏感元素必须由调用方传入脱敏值或哈希值
     */
    @Transactional(rollbackFor = Exception.class)
    public void createTradeBlack(RiskDTOs.TradeBlackSaveRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantId", trim(request.getMerchantId()));
        data.put("merchantName", defaultIfBlank(request.getMerchantName(), riskManagementMapper.selectMerchantName(trim(request.getMerchantId()))));
        data.put("merchantOrderNo", trim(request.getMerchantOrderNo()));
        data.put("paymentOrderNo", trim(request.getPaymentOrderNo()));
        data.put("blackTargetType", trim(request.getBlackTargetType()));
        data.put("blackTargetValueMasked", trim(request.getBlackTargetValueMasked()));
        data.put("blackTargetHash", trim(request.getBlackTargetHash()));
        data.put("sourceType", SOURCE_MANUAL);
        data.put("actionType", defaultIfBlank(request.getActionType(), "ADD"));
        data.put("actionReason", trim(request.getActionReason()));
        data.put("status", defaultStatus(request.getStatus()));
        riskManagementMapper.insertTradeBlack(data, currentOperatorName());
        riskManagementMapper.insertChangeLog("TRADE_BLACK", "system", null, "CREATE", null, JsonUtils.toJsonString(data), currentOperatorName(), "系统交易加黑");
    }

    /**
     * 解除系统交易加黑记录。
     *
     * @param id     系统交易加黑记录ID
     * @param reason 解除原因，允许为空
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseTradeBlack(Long id, String reason) {
        int rows = riskManagementMapper.releaseTradeBlack(id, reason, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "trade black record not found");
        }
        riskManagementMapper.insertChangeLog("TRADE_BLACK", "system", id, "RELEASE", null, JsonUtils.toJsonString(Map.of("reason", defaultIfBlank(reason, ""))), currentOperatorName(), "解除系统交易加黑");
    }

    /**
     * 导出名单或规则 CSV，最多导出前 5000 条配置记录。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param response     HTTP 响应，方法内部写入 UTF-8 BOM CSV
     */
    public void export(String moduleType, String functionCode, HttpServletResponse response) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "export");
        List<Map<String, Object>> rows;
        if (definition.isRuleFunction()) {
            rows = riskManagementMapper.selectRulePage(definition.getTableName(), null, null, null, null, null, 0, 5000);
        } else if (definition.isRegionFunction()) {
            rows = riskManagementMapper.selectRegionPage(null, null, null, null, null, 0, 5000);
        } else {
            rows = riskManagementMapper.selectListPage(definition.getTableName(), null, null, null, null, null, null, 0, 5000, hasCountryFields(definition));
        }
        writeCsv(definition.getFunctionCode() + ".csv", rows.stream().map(row -> sanitizeExportRow(definition, row)).toList(), response);
    }

    /**
     * 下载导入模板 CSV。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param response     HTTP 响应，方法内部写入 UTF-8 BOM CSV
     */
    public void template(String moduleType, String functionCode, HttpServletResponse response) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "template");
        List<Map<String, Object>> rows = List.of(templateRow(definition));
        writeCsv(definition.getFunctionCode() + "-template.csv", rows, response);
    }

    /**
     * 批量导入名单或规则 CSV。导入过程使用同一事务，任一数据行失败则整体回滚。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param file         CSV 文件
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.ImportResultResponse importCsv(String moduleType, String functionCode, MultipartFile file) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "import");
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "import file is required");
        }
        List<Map<String, String>> rows = readCsv(file);
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            int lineNo = index + 2;
            try {
                if (definition.isRegionFunction()) {
                    Map<String, Object> data = regionData(toRegionRequest(row), SOURCE_IMPORT);
                    ensureRegionNotDuplicated(null, data);
                    riskManagementMapper.insertRegion(data, currentOperatorName());
                } else if (definition.isRuleFunction()) {
                    riskManagementMapper.insertRule(definition.getTableName(), ruleData(toRuleRequest(row)), currentOperatorName());
                } else {
                    Map<String, Object> data = listData(definition, toListRequest(row), SOURCE_IMPORT);
                    ensureListNotDuplicated(definition, null, data);
                    riskManagementMapper.insertListRecord(definition.getTableName(), data, currentOperatorName(), hasRangeFields(definition), hasCardBrandField(definition), hasCountryFields(definition), hasCountryNumericField(definition));
                }
                successCount++;
            } catch (RuntimeException exception) {
                errors.add("line " + lineNo + ": " + exception.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), String.join("; ", errors));
        }
        riskManagementMapper.insertChangeLog(definition.getModuleType(), definition.getFunctionCode(), null, "IMPORT", null, JsonUtils.toJsonString(Map.of("successCount", successCount)), currentOperatorName(), definition.getFunctionName());
        RiskDTOs.ImportResultResponse response = new RiskDTOs.ImportResultResponse();
        response.setSuccessCount(successCount);
        response.setFailureCount(0);
        response.setErrors(List.of());
        return response;
    }

    private RiskDTOs.FunctionDefinitionResponse toDefinitionResponse(RiskFunctionDefinition definition) {
        RiskDTOs.FunctionDefinitionResponse response = new RiskDTOs.FunctionDefinitionResponse();
        response.setModuleType(definition.getModuleType());
        response.setFunctionCode(definition.getFunctionCode());
        response.setFunctionName(definition.getFunctionName());
        response.setRoutePath(definition.getRoutePath());
        response.setPermissionPrefix(definition.getPermissionPrefix());
        response.setRegionFunction(definition.isRegionFunction());
        response.setRuleFunction(definition.isRuleFunction());
        return response;
    }

    private Map<String, Object> listData(RiskFunctionDefinition definition, RiskDTOs.RiskListSaveRequest request, String sourceType) {
        if (isCountryListFunction(definition)) {
            normalizeCountryListRequest(request);
        }
        RiskListValueNormalizer.NormalizedValue normalizedValue = riskListValueNormalizer.normalize(definition, request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantScope", defaultIfBlank(request.getMerchantScope(), DEFAULT_SCOPE));
        data.put("merchantId", trim(request.getMerchantId()));
        data.put("matchValueMasked", normalizedValue.matchValueMasked());
        data.put("matchValueHash", normalizedValue.matchValueHash());
        data.put("matchValueCipher", normalizedValue.matchValueCipher());
        data.put("matchValueStart", normalizedValue.matchValueStart());
        data.put("matchValueEnd", normalizedValue.matchValueEnd());
        data.put("matchValueStartNumber", normalizedValue.matchValueStartNumber());
        data.put("matchValueEndNumber", normalizedValue.matchValueEndNumber());
        data.put("ipVersion", normalizedValue.ipVersion());
        data.put("cardBrand", resolveCardBrand(definition, request));
        data.put("countryAlpha2", upper(request.getCountryAlpha2()));
        data.put("countryAlpha3", upper(request.getCountryAlpha3()));
        data.put("countryNumeric", trim(request.getCountryNumeric()));
        data.put("riskLevel", defaultIfBlank(request.getRiskLevel(), defaultRiskLevel(definition)));
        data.put("decisionAction", defaultIfBlank(request.getDecisionAction(), defaultDecisionAction(definition)));
        data.put("effectiveTime", defaultEffectiveTime(request.getEffectiveTime()));
        applyValidity(data, request);
        data.put("sourceType", sourceType);
        data.put("status", defaultStatus(request.getStatus()));
        data.put("remark", trim(request.getRemark()));
        normalizeScope(data);
        if (data.get("merchantId") == null) {
            data.put("merchantId", "");
        }
        return data;
    }

    private List<RiskDTOs.RiskListSaveRequest> expandCountryListRequests(RiskFunctionDefinition definition, RiskDTOs.RiskListSaveRequest request) {
        if (!isCountryListFunction(definition) || request.getCountryAlpha2List() == null || request.getCountryAlpha2List().isEmpty()) {
            return List.of(request);
        }
        return request.getCountryAlpha2List().stream()
                .map(this::upper)
                .filter(StringUtils::hasText)
                .distinct()
                .map(countryAlpha2 -> copyCountryListRequest(request, countryAlpha2))
                .toList();
    }

    private RiskDTOs.RiskListSaveRequest copyCountryListRequest(RiskDTOs.RiskListSaveRequest source, String countryAlpha2) {
        RiskDTOs.RiskListSaveRequest target = new RiskDTOs.RiskListSaveRequest();
        target.setMerchantScope(source.getMerchantScope());
        target.setMerchantId(source.getMerchantId());
        target.setRuleName(source.getRuleName());
        target.setMatchValuePlain(countryAlpha2);
        target.setMatchValueMasked(source.getMatchValueMasked());
        target.setMatchValueHash(source.getMatchValueHash());
        target.setMatchValueStart(source.getMatchValueStart());
        target.setMatchValueEnd(source.getMatchValueEnd());
        target.setIpVersion(source.getIpVersion());
        target.setCardBrand(source.getCardBrand());
        target.setCountryAlpha2(countryAlpha2);
        target.setCountryAlpha3(source.getCountryAlpha3());
        target.setCountryNumeric(source.getCountryNumeric());
        target.setRiskLevel(source.getRiskLevel());
        target.setDecisionAction(source.getDecisionAction());
        target.setEffectiveTime(source.getEffectiveTime());
        target.setValidityType(source.getValidityType());
        target.setValidityDays(source.getValidityDays());
        target.setSourceType(source.getSourceType());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        return target;
    }

    private void normalizeCountryListRequest(RiskDTOs.RiskListSaveRequest request) {
        String countryAlpha2 = upper(defaultIfBlank(request.getCountryAlpha2(), request.getMatchValuePlain()));
        if (!StringUtils.hasText(countryAlpha2)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择国家/地区");
        }
        Map<String, Object> country = riskManagementMapper.selectCountryOptionByAlpha2(countryAlpha2);
        if (country == null || country.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "国家/地区不存在或已停用");
        }
        String countryAlpha3 = upper(asString(country.get("extra")));
        request.setCountryAlpha2(countryAlpha2);
        request.setCountryAlpha3(countryAlpha3);
        request.setCountryNumeric(asString(country.get("numericCode")));
        request.setMatchValuePlain(countryAlpha3);
    }

    private Map<String, Object> regionData(RiskDTOs.RegionSaveRequest request, String sourceType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantScope", defaultIfBlank(request.getMerchantScope(), DEFAULT_SCOPE));
        data.put("merchantId", trim(request.getMerchantId()));
        data.put("regionMatchLevel", defaultIfBlank(request.getRegionMatchLevel(), "COUNTRY").toUpperCase(Locale.ROOT));
        data.put("countryAlpha2", upper(request.getCountryAlpha2()));
        applyCountryMetadata(data);
        data.put("stateProvinceName", trim(request.getStateProvinceName()));
        data.put("cityName", trim(request.getCityName()));
        normalizeRegionLevelFields(data);
        data.put("riskLevel", defaultIfBlank(request.getRiskLevel(), "HIGH"));
        data.put("decisionAction", defaultIfBlank(request.getDecisionAction(), "REJECT"));
        data.put("effectiveTime", defaultEffectiveTime(request.getEffectiveTime()));
        applyRegionValidity(data, request);
        data.put("sourceType", sourceType);
        data.put("status", defaultStatus(request.getStatus()));
        data.put("remark", trim(request.getRemark()));
        normalizeScope(data);
        if (data.get("merchantId") == null) {
            data.put("merchantId", "");
        }
        return data;
    }

    private List<String> regionCreateCountryCodes(RiskDTOs.RegionSaveRequest request) {
        String regionMatchLevel = defaultIfBlank(request.getRegionMatchLevel(), "COUNTRY").toUpperCase(Locale.ROOT);
        List<String> sourceCodes = "COUNTRY".equals(regionMatchLevel) && request.getCountryAlpha2List() != null && !request.getCountryAlpha2List().isEmpty()
                ? request.getCountryAlpha2List()
                : List.of(defaultIfBlank(request.getCountryAlpha2(), ""));
        List<String> countryCodes = sourceCodes.stream()
                .map(this::upper)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (countryCodes.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择国家/地区");
        }
        return countryCodes;
    }

    private Map<String, Object> ruleData(RiskDTOs.RiskRuleSaveRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantScope", defaultIfBlank(request.getMerchantScope(), DEFAULT_SCOPE));
        data.put("merchantId", trim(request.getMerchantId()));
        data.put("ruleName", trim(request.getRuleName()));
        data.put("matchMode", trim(request.getMatchMode()));
        data.put("matchValue", trim(request.getMatchValue()));
        data.put("limitType", trim(request.getLimitType()));
        data.put("amountMin", request.getAmountMin());
        data.put("amountMax", request.getAmountMax());
        data.put("currency", upper(request.getCurrency()));
        data.put("timeWindowSeconds", request.getTimeWindowSeconds());
        data.put("thresholdCount", request.getThresholdCount());
        data.put("elementsJson", defaultIfBlank(request.getElementsJson(), "{}"));
        data.put("riskLevel", defaultIfBlank(request.getRiskLevel(), DEFAULT_RISK_LEVEL));
        data.put("decisionAction", defaultIfBlank(request.getDecisionAction(), DEFAULT_DECISION_ACTION));
        data.put("effectiveTime", request.getEffectiveTime());
        data.put("expireTime", request.getExpireTime());
        data.put("status", defaultStatus(request.getStatus()));
        data.put("remark", trim(request.getRemark()));
        normalizeScope(data);
        validateRuleData(data);
        return data;
    }

    private Map<String, Object> requireRecord(String tableName, Long id) {
        if (id == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "id is required");
        }
        Map<String, Object> record = riskManagementMapper.selectById(tableName, id);
        if (record == null || record.isEmpty()) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "risk record not found");
        }
        return record;
    }

    private RiskDTOs.RiskRecordResponse latestListRecord(RiskFunctionDefinition definition, Map<String, Object> data) {
        List<Map<String, Object>> rows;
        if (definition.isRuleFunction()) {
            rows = riskManagementMapper.selectRulePage(definition.getTableName(), (String) data.get("merchantId"), (String) data.get("ruleName"), (String) data.get("matchValue"), (String) data.get("currency"), (Integer) data.get("status"), 0, 1);
        } else if (definition.isRegionFunction()) {
            rows = riskManagementMapper.selectRegionPage((String) data.get("merchantScope"), (String) data.get("merchantId"), null, (String) data.get("countryAlpha3"), (Integer) data.get("status"), 0, 1);
        } else {
            rows = riskManagementMapper.selectListPage(definition.getTableName(), (String) data.get("merchantScope"), (String) data.get("merchantId"), (String) data.get("matchValueMasked"), null, (String) data.get("countryAlpha3"), (Integer) data.get("status"), 0, 1, hasCountryFields(definition));
        }
        return rows.isEmpty() ? new RiskDTOs.RiskRecordResponse() : toRecordResponse(rows.get(0));
    }

    private void writeChange(RiskFunctionDefinition definition, Long businessId, String operationType, Map<String, Object> before, Map<String, Object> after) {
        riskManagementMapper.insertChangeLog(
                definition.getModuleType(),
                definition.getFunctionCode(),
                businessId,
                operationType,
                before == null ? null : JsonUtils.toJsonString(sanitizeSnapshot(before)),
                after == null ? null : JsonUtils.toJsonString(sanitizeSnapshot(after)),
                currentOperatorName(),
                definition.getFunctionName()
        );
    }

    private RiskDTOs.RiskRecordResponse toRecordResponse(Map<String, Object> row) {
        RiskDTOs.RiskRecordResponse response = new RiskDTOs.RiskRecordResponse();
        response.setId(asLong(row.get("id")));
        response.setMerchantScope(asString(row.get("merchant_scope")));
        response.setMerchantId(asString(row.get("merchant_id")));
        response.setMerchantName(defaultIfBlank(asString(row.get("merchant_name")), riskManagementMapper.selectMerchantName(response.getMerchantId())));
        response.setRuleName(asString(row.get("rule_name")));
        response.setMatchValueMasked(asString(row.get("match_value_masked")));
        response.setMatchValueStart(asString(row.get("match_value_start")));
        response.setMatchValueEnd(asString(row.get("match_value_end")));
        response.setIpVersion(asString(row.get("ip_version")));
        response.setCardBrand(asString(row.get("card_brand")));
        response.setCountryAlpha2(asString(row.get("country_alpha2")));
        response.setCountryAlpha3(asString(row.get("country_alpha3")));
        response.setCountryNumeric(asString(row.get("country_numeric")));
        if (!StringUtils.hasText(response.getCountryAlpha2()) && StringUtils.hasText(response.getCountryAlpha3())) {
            Map<String, Object> country = riskManagementMapper.selectCountryOptionByAlpha3(response.getCountryAlpha3());
            response.setCountryAlpha2(asString(country == null ? null : country.get("value")));
        }
        response.setRiskLevel(asString(row.get("risk_level")));
        response.setDecisionAction(asString(row.get("decision_action")));
        response.setEffectiveTime(asLocalDateTime(row.get("effective_time")));
        response.setExpireTime(asLocalDateTime(row.get("expire_time")));
        response.setValidityType(asString(row.get("validity_type")));
        response.setValidityDays(asInteger(row.get("validity_days")));
        response.setSourceType(asString(row.get("source_type")));
        response.setStatus(asInteger(row.get("status")));
        response.setRemark(asString(row.get("remark")));
        response.setCreateBy(asString(row.get("create_by")));
        response.setUpdateBy(asString(row.get("update_by")));
        response.setCreateTime(asLocalDateTime(row.get("create_time")));
        response.setUpdateTime(asLocalDateTime(row.get("update_time")));
        response.setRegionMatchLevel(asString(row.get("region_match_level")));
        response.setStateProvinceCode(asString(row.get("state_province_code")));
        response.setStateProvinceName(asString(row.get("state_province_name")));
        response.setCityCode(asString(row.get("city_code")));
        response.setCityName(asString(row.get("city_name")));
        response.setMatchMode(asString(row.get("match_mode")));
        response.setMatchValue(asString(row.get("match_value")));
        response.setLimitType(asString(row.get("limit_type")));
        response.setAmountMin(asBigDecimal(row.get("amount_min")));
        response.setAmountMax(asBigDecimal(row.get("amount_max")));
        response.setCurrency(asString(row.get("currency")));
        response.setTimeWindowSeconds(asInteger(row.get("time_window_seconds")));
        response.setThresholdCount(asInteger(row.get("threshold_count")));
        response.setElementsJson(asString(row.get("elements_json")));
        return response;
    }

    private void ensureListNotDuplicated(RiskFunctionDefinition definition, Long excludeId, Map<String, Object> data) {
        long duplicateCount = riskManagementMapper.countListDuplicate(
                definition.getTableName(),
                (String) data.get("merchantScope"),
                (String) data.get("merchantId"),
                (String) data.get("matchValueHash"),
                excludeId
        );
        if (duplicateCount > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "same risk list record already exists");
        }
    }

    private void ensureRegionNotDuplicated(Long excludeId, Map<String, Object> data) {
        long duplicateCount = riskManagementMapper.countRegionDuplicate(
                (String) data.get("merchantScope"),
                (String) data.get("merchantId"),
                (String) data.get("regionMatchLevel"),
                (String) data.get("countryAlpha3"),
                (String) data.get("stateProvinceName"),
                (String) data.get("cityName"),
                excludeId
        );
        if (duplicateCount > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一生效范围下已存在相同高风险区域");
        }
    }

    private void validateRuleData(Map<String, Object> data) {
        BigDecimal amountMin = (BigDecimal) data.get("amountMin");
        BigDecimal amountMax = (BigDecimal) data.get("amountMax");
        if (amountMin != null && amountMax != null && amountMin.compareTo(amountMax) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "最小金额不能大于最大金额");
        }
        Integer timeWindowSeconds = (Integer) data.get("timeWindowSeconds");
        if (timeWindowSeconds != null && timeWindowSeconds <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "时间窗口秒数必须大于 0");
        }
        Integer thresholdCount = (Integer) data.get("thresholdCount");
        if (thresholdCount != null && thresholdCount <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "阈值次数必须大于 0");
        }
        String elementsJson = (String) data.get("elementsJson");
        try {
            JsonUtils.parseObject(elementsJson, Map.class);
        } catch (RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "组合元素 JSON 必须是合法对象");
        }
    }

    private void applyCountryMetadata(Map<String, Object> data) {
        String countryAlpha2 = (String) data.get("countryAlpha2");
        if (!StringUtils.hasText(countryAlpha2)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择国家/地区");
        }
        Map<String, Object> country = riskManagementMapper.selectCountryOptionByAlpha2(countryAlpha2);
        if (country == null || country.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "国家/地区不存在或已停用");
        }
        data.put("countryAlpha3", upper(asString(country.get("extra"))));
    }

    private String countryAlpha3FromAlpha2(String countryAlpha2) {
        if (!StringUtils.hasText(countryAlpha2)) {
            return null;
        }
        Map<String, Object> country = riskManagementMapper.selectCountryOptionByAlpha2(upper(countryAlpha2));
        if (country == null || country.isEmpty()) {
            return "__INVALID_COUNTRY__";
        }
        return upper(asString(country.get("extra")));
    }

    private void normalizeRegionLevelFields(Map<String, Object> data) {
        String level = (String) data.get("regionMatchLevel");
        if (!List.of("COUNTRY", "STATE", "CITY").contains(level)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "区域级别不正确");
        }
        if ("COUNTRY".equals(level)) {
            data.put("stateProvinceName", "");
            data.put("cityName", "");
            return;
        }
        if (!StringUtils.hasText((String) data.get("stateProvinceName"))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择或输入州/省");
        }
        if ("STATE".equals(level)) {
            data.put("cityName", "");
            return;
        }
        if (!StringUtils.hasText((String) data.get("cityName"))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择或输入城市");
        }
    }

    private String resolveCardBrand(RiskFunctionDefinition definition, RiskDTOs.RiskListSaveRequest request) {
        if (!isCardNumberFunction(definition)) {
            return trim(request.getCardBrand());
        }
        String cardNo = defaultIfBlank(request.getMatchValuePlain(), request.getMatchValueMasked());
        if (!StringUtils.hasText(cardNo)) {
            return null;
        }
        String digits = cardNo.replaceAll("\\s+", "");
        if (!digits.matches("\\d{12,19}")) {
            return null;
        }
        return detectCardBrand(digits);
    }

    private boolean isCardNumberFunction(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        return "cardNo".equals(code) || "card".equals(code);
    }

    private String detectCardBrand(String cardNo) {
        if (cardNo.matches("^4.*")) {
            return "VISA";
        }
        if (cardNo.matches("^(5[1-5]|2[2-7]).*")) {
            return "MASTERCARD";
        }
        if (cardNo.matches("^3[47].*")) {
            return "AMEX";
        }
        if (cardNo.matches("^35.*")) {
            return "JCB";
        }
        if (cardNo.matches("^62.*")) {
            return "UNIONPAY";
        }
        if (cardNo.matches("^(6011|65|64[4-9]).*")) {
            return "DISCOVER";
        }
        if (cardNo.matches("^(30[0-5]|36|38|39).*")) {
            return "DINERS_CLUB";
        }
        if (cardNo.matches("^(50|5[6-9]|6[0-9]).*")) {
            return "MAESTRO";
        }
        return null;
    }

    private void normalizeScope(Map<String, Object> data) {
        String merchantScope = defaultIfBlank((String) data.get("merchantScope"), DEFAULT_SCOPE).toUpperCase(Locale.ROOT);
        data.put("merchantScope", merchantScope);
        if (DEFAULT_SCOPE.equals(merchantScope)) {
            data.put("merchantId", null);
            return;
        }
        if (!StringUtils.hasText((String) data.get("merchantId"))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户风控必须选择商户号");
        }
    }

    private LocalDateTime defaultEffectiveTime(LocalDateTime effectiveTime) {
        return effectiveTime == null ? LocalDateTime.now() : effectiveTime;
    }

    private void applyValidity(Map<String, Object> data, RiskDTOs.RiskListSaveRequest request) {
        String validityType = defaultIfBlank(request.getValidityType(), VALIDITY_SUPER_LONG).toUpperCase(Locale.ROOT);
        data.put("validityType", validityType);
        if (VALIDITY_SUPER_LONG.equals(validityType)) {
            data.put("validityDays", null);
            data.put("expireTime", null);
            return;
        }
        Integer validityDays = request.getValidityDays();
        if (validityDays == null || validityDays <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入有效天数");
        }
        if (VALIDITY_LONG.equals(validityType) && validityDays < LONG_VALIDITY_MIN_DAYS) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "长期有效期至少 120 天");
        }
        if (!VALIDITY_LONG.equals(validityType) && !VALIDITY_LIMITED.equals(validityType)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "有效期类型不正确");
        }
        LocalDateTime effectiveTime = (LocalDateTime) data.get("effectiveTime");
        data.put("validityDays", validityDays);
        data.put("expireTime", effectiveTime.plusDays(validityDays));
    }

    private void applyRegionValidity(Map<String, Object> data, RiskDTOs.RegionSaveRequest request) {
        String validityType = defaultIfBlank(request.getValidityType(), VALIDITY_SUPER_LONG).toUpperCase(Locale.ROOT);
        data.put("validityType", validityType);
        if (VALIDITY_SUPER_LONG.equals(validityType)) {
            data.put("validityDays", null);
            data.put("expireTime", null);
            return;
        }
        Integer validityDays = request.getValidityDays();
        if (validityDays == null || validityDays <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入有效天数");
        }
        if (VALIDITY_LONG.equals(validityType) && validityDays < LONG_VALIDITY_MIN_DAYS) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "长期有效期至少 120 天");
        }
        if (!VALIDITY_LONG.equals(validityType) && !VALIDITY_LIMITED.equals(validityType)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "有效期类型不正确");
        }
        LocalDateTime effectiveTime = (LocalDateTime) data.get("effectiveTime");
        data.put("validityDays", validityDays);
        data.put("expireTime", effectiveTime.plusDays(validityDays));
    }

    private String defaultRiskLevel(RiskFunctionDefinition definition) {
        if ("AML".equalsIgnoreCase(definition.getModuleType())) {
            return "CRITICAL";
        }
        if ("BLACK".equalsIgnoreCase(definition.getModuleType())) {
            return "HIGH";
        }
        if ("WHITE".equalsIgnoreCase(definition.getModuleType())) {
            return "LOW";
        }
        return DEFAULT_RISK_LEVEL;
    }

    private String defaultDecisionAction(RiskFunctionDefinition definition) {
        if ("WHITE".equalsIgnoreCase(definition.getModuleType())) {
            return "PASS";
        }
        if ("AML".equalsIgnoreCase(definition.getModuleType()) || "BLACK".equalsIgnoreCase(definition.getModuleType())) {
            return "REJECT";
        }
        return DEFAULT_DECISION_ACTION;
    }

    private Map<String, Object> sanitizeSnapshot(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        copy.remove("match_value_cipher");
        copy.remove("matchValueCipher");
        copy.remove("match_value_hash");
        copy.remove("matchValueHash");
        copy.remove("match_value_start_number");
        copy.remove("matchValueStartNumber");
        copy.remove("match_value_end_number");
        copy.remove("matchValueEndNumber");
        copy.remove("ip_version");
        copy.remove("ipVersion");
        return copy;
    }

    private List<RiskDTOs.OptionItem> toOptions(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            RiskDTOs.OptionItem item = option(asString(row.get("label")), asString(row.get("value")), asString(row.get("extra")));
            item.setNumericCode(asString(row.get("numericCode")));
            item.setFlagEmoji(asString(row.get("flagEmoji")));
            item.setContinentCode(asString(row.get("continentCode")));
            item.setContinentName(asString(row.get("continentName")));
            return item;
        }).toList();
    }

    private RiskDTOs.OptionItem option(String label, String value, String extra) {
        RiskDTOs.OptionItem item = new RiskDTOs.OptionItem();
        item.setLabel(label);
        item.setValue(value);
        item.setExtra(extra);
        return item;
    }

    private void ensureFunctionPermission(RiskFunctionDefinition definition, String action) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        List<String> permissions = account.getPermissions();
        if (permissions.contains("*:*:*")) {
            return;
        }
        String requiredPermission = definition.getPermissionPrefix() + ":" + action;
        if (!permissions.contains(requiredPermission)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }

    private Map<String, Object> templateRow(RiskFunctionDefinition definition) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (definition.isRegionFunction()) {
            row.put("merchantScope", "GLOBAL");
            row.put("merchantId", "");
            row.put("regionMatchLevel", "CITY");
            row.put("countryAlpha2", "US");
            row.put("stateProvinceName", "California");
            row.put("cityName", "Los Angeles");
            row.put("validityType", VALIDITY_SUPER_LONG);
            row.put("validityDays", "");
            row.put("sourceType", SOURCE_IMPORT);
        } else if (definition.isRuleFunction()) {
            row.put("ruleName", "示例规则");
            row.put("merchantScope", "GLOBAL");
            row.put("merchantId", "");
            row.put("matchMode", "EXACT");
            row.put("matchValue", "");
            row.put("limitType", "SINGLE_MAX");
            row.put("amountMin", "");
            row.put("amountMax", BigDecimal.ZERO);
            row.put("currency", "USD");
            row.put("timeWindowSeconds", "");
            row.put("thresholdCount", "");
            row.put("elementsJson", "{}");
        } else {
            row.put("merchantScope", "GLOBAL");
            row.put("merchantId", "");
            fillListTemplateValue(definition, row);
            row.put("validityType", VALIDITY_SUPER_LONG);
            row.put("validityDays", "");
            row.put("sourceType", SOURCE_IMPORT);
            pruneListTemplateRow(definition, row);
        }
        row.put("riskLevel", DEFAULT_RISK_LEVEL);
        row.put("decisionAction", DEFAULT_DECISION_ACTION);
        row.put("status", ENABLED);
        row.put("remark", "导入时请删除示例行");
        return row;
    }

    private void fillListTemplateValue(RiskFunctionDefinition definition, Map<String, Object> row) {
        String code = definition.getFunctionCode();
        row.put("matchValuePlain", "example");
        row.put("matchValueStart", "");
        row.put("matchValueEnd", "");
        if ("cardNo".equals(code) || "card".equals(code)) {
            row.put("matchValuePlain", "4111111111111111");
        } else if ("cardBin".equals(code)) {
            row.put("matchValuePlain", "");
            row.put("matchValueStart", "411111");
            row.put("matchValueEnd", "411111");
        } else if ("ip".equals(code)) {
            row.put("matchValuePlain", "");
            row.put("matchValueStart", "203.0.113.10");
            row.put("matchValueEnd", "WHITE".equals(definition.getModuleType()) ? "203.0.113.10" : "203.0.113.20");
        } else if ("country".equals(code) || code.endsWith("Country") || code.contains("Country")) {
            row.put("matchValuePlain", "US");
            row.put("countryAlpha2", "US");
            row.put("countryAlpha3", "USA");
            row.put("countryNumeric", "840");
        } else if ("email".equals(code)) {
            row.put("matchValuePlain", "risk@example.com");
        } else if ("emailDomain".equals(code)) {
            row.put("matchValuePlain", "example.com");
        } else if ("phone".equals(code)) {
            row.put("matchValuePlain", "+12025550123");
        } else if ("sourceUrl".equals(code)) {
            row.put("matchValuePlain", "example.com");
        }
    }

    private void pruneListTemplateRow(RiskFunctionDefinition definition, Map<String, Object> row) {
        if (!hasRangeFields(definition)) {
            row.remove("matchValueStart");
            row.remove("matchValueEnd");
        }
        if (!hasCardBrandField(definition)) {
            row.remove("cardBrand");
        }
        if (!hasCountryFields(definition)) {
            row.remove("countryAlpha2");
            row.remove("countryAlpha3");
            row.remove("countryNumeric");
        }
    }

    private List<Map<String, String>> readCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                return List.of();
            }
            String[] headers = parseCsvLine(stripBom(headerLine)).toArray(String[]::new);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.length; index++) {
                    row.put(headers[index], index < values.size() ? values.get(index) : null);
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "read import csv failed");
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private RiskDTOs.RiskListSaveRequest toListRequest(Map<String, String> row) {
        RiskDTOs.RiskListSaveRequest request = new RiskDTOs.RiskListSaveRequest();
        request.setMerchantScope(value(row, "merchantScope"));
        request.setMerchantId(value(row, "merchantId"));
        request.setRuleName(value(row, "ruleName"));
        request.setMatchValuePlain(value(row, "matchValuePlain"));
        request.setMatchValueMasked(value(row, "matchValueMasked"));
        request.setMatchValueHash(value(row, "matchValueHash"));
        request.setMatchValueStart(value(row, "matchValueStart"));
        request.setMatchValueEnd(value(row, "matchValueEnd"));
        request.setIpVersion(value(row, "ipVersion"));
        request.setCardBrand(value(row, "cardBrand"));
        request.setCountryAlpha2(value(row, "countryAlpha2"));
        request.setCountryAlpha3(value(row, "countryAlpha3"));
        request.setCountryNumeric(value(row, "countryNumeric"));
        request.setRiskLevel(value(row, "riskLevel"));
        request.setDecisionAction(value(row, "decisionAction"));
        request.setValidityType(value(row, "validityType"));
        request.setValidityDays(intValue(row, "validityDays"));
        request.setSourceType(SOURCE_IMPORT);
        request.setStatus(intValue(row, "status"));
        request.setRemark(value(row, "remark"));
        return request;
    }

    private RiskDTOs.RegionSaveRequest toRegionRequest(Map<String, String> row) {
        RiskDTOs.RegionSaveRequest request = new RiskDTOs.RegionSaveRequest();
        request.setMerchantScope(value(row, "merchantScope"));
        request.setMerchantId(value(row, "merchantId"));
        request.setRuleName(value(row, "ruleName"));
        request.setRegionMatchLevel(required(row, "regionMatchLevel"));
        request.setCountryAlpha2(required(row, "countryAlpha2"));
        request.setStateProvinceName(value(row, "stateProvinceName"));
        request.setCityName(value(row, "cityName"));
        request.setRiskLevel(value(row, "riskLevel"));
        request.setDecisionAction(value(row, "decisionAction"));
        request.setValidityType(value(row, "validityType"));
        request.setValidityDays(intValue(row, "validityDays"));
        request.setSourceType(SOURCE_IMPORT);
        request.setStatus(intValue(row, "status"));
        request.setRemark(value(row, "remark"));
        return request;
    }

    private RiskDTOs.RiskRuleSaveRequest toRuleRequest(Map<String, String> row) {
        RiskDTOs.RiskRuleSaveRequest request = new RiskDTOs.RiskRuleSaveRequest();
        request.setMerchantScope(value(row, "merchantScope"));
        request.setMerchantId(value(row, "merchantId"));
        request.setRuleName(required(row, "ruleName"));
        request.setMatchMode(value(row, "matchMode"));
        request.setMatchValue(value(row, "matchValue"));
        request.setLimitType(value(row, "limitType"));
        request.setAmountMin(decimalValue(row, "amountMin"));
        request.setAmountMax(decimalValue(row, "amountMax"));
        request.setCurrency(value(row, "currency"));
        request.setTimeWindowSeconds(intValue(row, "timeWindowSeconds"));
        request.setThresholdCount(intValue(row, "thresholdCount"));
        request.setElementsJson(value(row, "elementsJson"));
        request.setRiskLevel(value(row, "riskLevel"));
        request.setDecisionAction(value(row, "decisionAction"));
        request.setEffectiveTime(localDateTimeValue(row, "effectiveTime"));
        request.setExpireTime(localDateTimeValue(row, "expireTime"));
        request.setStatus(intValue(row, "status"));
        request.setRemark(value(row, "remark"));
        return request;
    }

    private String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\ufeff' ? value.substring(1) : value;
    }

    private String required(Map<String, String> row, String key) {
        String text = value(row, key);
        if (!StringUtils.hasText(text)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), key + " is required");
        }
        return text;
    }

    private String value(Map<String, String> row, String key) {
        return trim(row.get(key));
    }

    private Integer intValue(Map<String, String> row, String key) {
        String text = value(row, key);
        return StringUtils.hasText(text) ? Integer.valueOf(text) : null;
    }

    private BigDecimal decimalValue(Map<String, String> row, String key) {
        String text = value(row, key);
        return StringUtils.hasText(text) ? new BigDecimal(text) : null;
    }

    private LocalDateTime localDateTimeValue(Map<String, String> row, String key) {
        String text = value(row, key);
        return StringUtils.hasText(text) ? LocalDateTime.parse(text.replace(" ", "T")) : null;
    }

    private void writeCsv(String fileName, List<Map<String, Object>> rows, HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=utf-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        try {
            List<String> headers = new ArrayList<>();
            if (!rows.isEmpty()) {
                headers.addAll(rows.get(0).keySet());
            }
            StringBuilder builder = new StringBuilder();
            builder.append('\ufeff');
            builder.append(String.join(",", headers)).append('\n');
            for (Map<String, Object> row : rows) {
                List<String> values = headers.stream().map(header -> csvValue(row.get(header))).toList();
                builder.append(String.join(",", values)).append('\n');
            }
            response.getWriter().write(builder.toString());
        } catch (IOException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "write csv failed");
        }
    }

    private String csvValue(Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        String escaped = raw.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private Map<String, Object> sanitizeExportRow(RiskFunctionDefinition definition, Map<String, Object> row) {
        Map<String, Object> copy = sanitizeSnapshot(row);
        copy.remove("match_value_cipher");
        copy.remove("match_value_hash");
        copy.remove("match_value_start_number");
        copy.remove("match_value_end_number");
        if (definition.isRegionFunction()) {
            copy.remove("country_alpha2");
            copy.remove("country_numeric");
            copy.remove("state_province_code");
            copy.remove("city_code");
            copy.remove("region_path_code");
            copy.remove("region_path_name");
        }
        if ("phone".equals(definition.getFunctionCode())) {
            copy.remove("match_value_start");
            copy.remove("match_value_end");
            copy.remove("card_brand");
            copy.remove("country_alpha2");
            copy.remove("country_alpha3");
            copy.remove("country_numeric");
        }
        if ("billingZip".equals(definition.getFunctionCode()) || "shippingZip".equals(definition.getFunctionCode())) {
            copy.remove("match_value_cipher");
            copy.remove("match_value_start");
            copy.remove("match_value_end");
            copy.remove("card_brand");
            copy.remove("country_alpha2");
            copy.remove("country_alpha3");
            copy.remove("country_numeric");
        }
        if ("ip".equals(definition.getFunctionCode())) {
            copy.remove("card_brand");
            copy.remove("country_alpha2");
            copy.remove("country_alpha3");
            copy.remove("country_numeric");
        }
        if (!hasRangeFields(definition)) {
            copy.remove("match_value_start");
            copy.remove("match_value_end");
        }
        if (!hasCardBrandField(definition)) {
            copy.remove("card_brand");
        }
        if (!hasCountryFields(definition)) {
            copy.remove("country_alpha2");
            copy.remove("country_alpha3");
            copy.remove("country_numeric");
        }
        return copy;
    }

    private boolean hasRangeFields(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        return "cardBin".equals(code) || "ip".equals(code);
    }

    private boolean hasCardBrandField(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        return "cardNo".equals(code) || "card".equals(code) || "cardBin".equals(code);
    }

    private String cardBinLookupNumber(RiskFunctionDefinition definition, String matchValue) {
        if (!"cardBin".equals(definition.getFunctionCode()) || !StringUtils.hasText(matchValue)) {
            return null;
        }
        String digits = matchValue.trim();
        if (!digits.matches("\\d{6,11}")) {
            return null;
        }
        return rightPad(digits, 11, '0');
    }

    private String rightPad(String value, int length, char ch) {
        if (value.length() >= length) {
            return value;
        }
        return value + String.valueOf(ch).repeat(length - value.length());
    }

    private boolean hasCountryFields(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        return "country".equals(code)
                || code.endsWith("Country")
                || code.contains("Country");
    }

    private boolean hasCountryNumericField(RiskFunctionDefinition definition) {
        return hasCountryFields(definition) && !isCountryListFunction(definition);
    }

    private boolean isCountryListFunction(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        return !definition.isRuleFunction() && ("country".equals(code) || code.endsWith("Country") || code.contains("Country"));
    }

    private long offset(long pageNo, long pageSize) {
        return Math.max(pageNo - 1, 0) * pageSize;
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount() : "admin";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Integer defaultStatus(Integer status) {
        return status == null ? ENABLED : status;
    }

    private String joinPath(Object... values) {
        List<String> parts = new ArrayList<>();
        for (Object value : values) {
            String text = value == null ? null : String.valueOf(value);
            if (StringUtils.hasText(text)) {
                parts.add(text);
            }
        }
        return String.join("/", parts);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return value == null ? null : LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    /**
     * 简单分页请求适配，用于变更日志列表。
     */
    public static class PageRequestAdapter extends com.scott.payment.component.core.model.PageRequest {
    }
}
