package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminSettlementReversalQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminSettlementReversalQueryService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 直接读取 transaction 逻辑数据源的冲正单分页和详情。
 * @status : create
 */
@Service
public class JdbcAdminSettlementReversalQueryService implements AdminSettlementReversalQueryService {

    /**
     * 默认页大小，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_PAGE_SIZE = 10;
    /**
     * {@code MAX_DATE_SPAN_DAYS}常量，统一 {@code JdbcAdminSettlementReversalQueryService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long MAX_DATE_SPAN_DAYS = 92;
    private static final Set<String> STATUSES = Set.of("PENDING_APPROVAL", "APPROVED", "REJECTED");
    private static final String COLUMNS = """
            id, reversal_order_no, original_batch_no, reversal_batch_no, merchant_id,
            settlement_account_id, target_currency, target_currency_exponent, net_direction,
            net_amount, reversal_status, submitted_by_account_id, submitted_by_account_name,
            submit_reason, submitted_time, decided_by_account_id, decided_by_account_name,
            decision_action, decision_comment, decision_time, version, create_time, update_time
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionLogicalReadExecutor readExecutor;
    private final int maxResultRows;

    @Autowired
    public JdbcAdminSettlementReversalQueryService(DataSource dataSource,
                                                   TransactionLogicalReadExecutor readExecutor,
                                                   TransactionShardingProperties shardingProperties,
                                                   TransactionQueryJdbcTemplateFactory factory) {
        this(factory.create(dataSource, shardingProperties), readExecutor, shardingProperties);
    }

    public JdbcAdminSettlementReversalQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                                   TransactionLogicalReadExecutor readExecutor,
                                                   TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.readExecutor = readExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
    }

    /**
     * 查询{@code search}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Override
    public PageResult<ReversalSummary> search(ReversalSearchRequest request,
                                              AdminMerchantDataScope dataScope) {
        ReversalSearchRequest query = normalize(request);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> page(query, scope));
    }

    /**
     * 查询指定业务单据详情，并执行调用方数据范围校验。
     * @param reversalOrderNo 结算预审单号或冲正单号，用于定位唯一业务单据
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 当前方法生成的 {@code ReversalDetailResponse} 结果
     */
    @Override
    public ReversalDetailResponse detail(String reversalOrderNo, AdminMerchantDataScope dataScope) {
        String orderNo = requireOrderNo(reversalOrderNo);
        AdminMerchantDataScope scope = requireScope(dataScope);
        return readExecutor.read(() -> detailLocal(orderNo, scope));
    }

    /**
     * 校验当前操作人是否有权访问指定业务单据。
     * <p>
     * 校验失败时按 运营后台服务 统一异常语义中断流程，不返回部分校验结果。
     * </p>
     * @param reversalOrderNo 结算预审单号或冲正单号，用于定位唯一业务单据
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     */
    @Override
    public void requireAccess(String reversalOrderNo, AdminMerchantDataScope dataScope) {
        detail(reversalOrderNo, dataScope);
    }

    private PageResult<ReversalSummary> page(ReversalSearchRequest query, AdminMerchantDataScope scope) {
        if (scope.empty()) return PageResult.of(0, query.getPageNo(), query.getPageSize(), List.of());
        StringBuilder where = new StringBuilder("WHERE submitted_time >= :beginTime AND submitted_time < :endTime\n");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("beginTime", query.getBeginSubmittedDate().atStartOfDay())
                .addValue("endTime", query.getEndSubmittedDate().plusDays(1).atStartOfDay())
                .addValue("permittedMerchantIds", scope.merchantIds());
        append(where, parameters, "reversal_order_no", "reversalOrderNo", query.getReversalOrderNo());
        append(where, parameters, "original_batch_no", "originalBatchNo", query.getOriginalBatchNo());
        append(where, parameters, "merchant_id", "merchantId", query.getMerchantId());
        append(where, parameters, "reversal_status", "reversalStatus", query.getReversalStatus());
        if (!scope.allMerchants()) where.append(" AND merchant_id IN (:permittedMerchantIds)\n");
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM settlement_reversal_order " + where,
                parameters, Long.class);
        long total = count == null ? 0 : count;
        long offset = (long) (query.getPageNo() - 1) * query.getPageSize();
        List<ReversalSummary> rows = offset >= total ? List.of() : jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM settlement_reversal_order " + where
                        + " ORDER BY submitted_time DESC, id DESC LIMIT :offset, :limit",
                new MapSqlParameterSource(parameters.getValues()).addValue("offset", offset)
                        .addValue("limit", query.getPageSize()),
                BeanPropertyRowMapper.newInstance(ReversalSummary.class));
        return PageResult.of(total, query.getPageNo(), query.getPageSize(), rows);
    }

    private ReversalDetailResponse detailLocal(String orderNo, AdminMerchantDataScope scope) {
        if (scope.empty()) throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        MapSqlParameterSource parameters = new MapSqlParameterSource("orderNo", orderNo)
                .addValue("permittedMerchantIds", scope.merchantIds());
        String scopeSql = scope.allMerchants() ? "" : " AND merchant_id IN (:permittedMerchantIds)";
        List<ReversalSummary> rows = jdbcTemplate.query("SELECT " + COLUMNS
                        + " FROM settlement_reversal_order WHERE reversal_order_no = :orderNo"
                        + scopeSql + " LIMIT 1", parameters,
                BeanPropertyRowMapper.newInstance(ReversalSummary.class));
        if (rows.isEmpty()) throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        ReversalAuditSnapshot audit = jdbcTemplate.queryForObject("""
                SELECT source_fingerprint, original_batch_version, original_net_result_item_id,
                       original_fund_ledger_id, submitted_role_snapshot, submit_client_ip,
                       submit_user_agent, decided_role_snapshot, decision_client_ip, decision_user_agent
                FROM settlement_reversal_order WHERE reversal_order_no = :orderNo
                """, parameters, BeanPropertyRowMapper.newInstance(ReversalAuditSnapshot.class));
        ReversalDetailResponse response = new ReversalDetailResponse();
        response.setReversal(rows.get(0));
        response.setSourceFingerprint(audit.getSourceFingerprint());
        response.setOriginalBatchVersion(audit.getOriginalBatchVersion());
        response.setOriginalNetResultItemId(audit.getOriginalNetResultItemId());
        response.setOriginalFundLedgerId(audit.getOriginalFundLedgerId());
        response.setSubmittedRoleSnapshot(audit.getSubmittedRoleSnapshot());
        response.setSubmitClientIp(audit.getSubmitClientIp());
        response.setSubmitUserAgent(audit.getSubmitUserAgent());
        response.setDecidedRoleSnapshot(audit.getDecidedRoleSnapshot());
        response.setDecisionClientIp(audit.getDecisionClientIp());
        response.setDecisionUserAgent(audit.getDecisionUserAgent());
        return response;
    }

    private ReversalSearchRequest normalize(ReversalSearchRequest request) {
        if (request == null || request.getBeginSubmittedDate() == null || request.getEndSubmittedDate() == null
                || request.getBeginSubmittedDate().isAfter(request.getEndSubmittedDate())
                || ChronoUnit.DAYS.between(request.getBeginSubmittedDate(), request.getEndSubmittedDate())
                > MAX_DATE_SPAN_DAYS) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        request.setReversalOrderNo(trim(request.getReversalOrderNo()));
        if (request.getReversalOrderNo() != null) requireOrderNo(request.getReversalOrderNo());
        request.setOriginalBatchNo(trim(request.getOriginalBatchNo()));
        request.setMerchantId(trim(request.getMerchantId()));
        request.setReversalStatus(trim(request.getReversalStatus()));
        if (request.getReversalStatus() != null) {
            request.setReversalStatus(request.getReversalStatus().toUpperCase(Locale.ROOT));
            if (!STATUSES.contains(request.getReversalStatus())) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        if (pageNo < 1 || pageSize < 1 || pageSize > maxResultRows) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        return request;
    }

    private void append(StringBuilder where, MapSqlParameterSource parameters,
                        String column, String parameter, String value) {
        if (value != null) {
            where.append(" AND ").append(column).append(" = :").append(parameter).append('\n');
            parameters.addValue(parameter, value);
        }
    }

    private String requireOrderNo(String value) {
        String normalized = trim(value);
        if (normalized == null || !normalized.matches("SRO\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    private AdminMerchantDataScope requireScope(AdminMerchantDataScope scope) {
        if (scope == null) throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        return scope;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Data
    private static class ReversalAuditSnapshot {
        /**
         * 冲正来源事实指纹，用于复核时确认原批次、净结果和资金流水未被替换。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String sourceFingerprint;
        /**
         * 申请冲正时读取的原结算批次版本号，用于复核阶段执行乐观一致性校验。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Long originalBatchVersion;
        /**
         * 原批次净入账结果明细主键，用于把冲正申请绑定到唯一结算结果。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Long originalNetResultItemId;
        /**
         * 原结算资金流水主键，用于防止同一入账流水被重复冲正。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private Long originalFundLedgerId;
        /**
         * 冲正申请人提交时的角色快照，用于 Maker-Checker 审计。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String submittedRoleSnapshot;
        /**
         * 冲正申请提交端 IP 快照，仅用于安全审计，展示和日志必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String submitClientIp;
        /**
         * 冲正申请提交端 User-Agent 快照，用于安全审计并限制长度。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String submitUserAgent;
        /**
         * 冲正复核人决策时的角色快照，用于 Maker-Checker 审计。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String decidedRoleSnapshot;
        /**
         * 冲正复核端 IP 快照，仅用于安全审计，展示和日志必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String decisionClientIp;
        /**
         * 冲正复核端 User-Agent 快照，用于安全审计并限制长度。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String decisionUserAgent;
    }
}
