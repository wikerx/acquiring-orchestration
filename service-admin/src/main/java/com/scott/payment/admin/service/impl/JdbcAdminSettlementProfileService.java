package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileUpdateRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminSettlementProfileService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminSettlementProfileService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Admin 结算档案 JDBC 实现；查询和 CAS 更新均限定交易逻辑数据源及 Admin 商户范围。
 * @status : create
 */
@Service
public class JdbcAdminSettlementProfileService implements AdminSettlementProfileService {

    /**
     * 默认页大小，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final Set<String> PROCESSING_MODES = Set.of("AUTO_POST", "AUTO_REVIEW", "MANUAL");
    private static final Set<String> PROFILE_STATUSES = Set.of("ACTIVE", "RETIRED", "SUSPENDED");
    private static final String PROFILE_COLUMNS = """
            profile.id, profile.settlement_profile_no, profile.merchant_id,
            merchant.merchant_name,
            profile.settlement_account_id,
            account.account_no AS settlement_account_no,
            account.account_status AS settlement_account_status,
            profile.target_currency, profile.target_currency_exponent,
            profile.business_time_zone, profile.daily_cutoff_time,
            profile.processing_mode, profile.profile_status,
            profile.effective_date, profile.expire_date, profile.version,
            profile.create_time, profile.update_time
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    private final int maxResultRows;

    /** 创建生产查询服务，并继承交易查询超时与结果预算。 */
    @Autowired
    public JdbcAdminSettlementProfileService(DataSource dataSource,
                                             TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                             TransactionShardingProperties shardingProperties,
                                             TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                transactionLogicalReadExecutor, shardingProperties);
    }

    /** 创建可注入 JDBC 模板的服务，供聚焦测试验证 SQL 与数据范围。 */
    public JdbcAdminSettlementProfileService(NamedParameterJdbcTemplate jdbcTemplate,
                                             TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                             TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
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
    public PageResult<ProfileSummary> search(ProfileSearchRequest request,
                                             AdminMerchantDataScope dataScope) {
        ProfileSearchRequest query = normalize(request);
        AdminMerchantDataScope scope = requiredScope(dataScope);
        return transactionLogicalReadExecutor.read(() -> searchNormalized(query, scope));
    }

    /**
     * 查询指定业务单据详情，并执行调用方数据范围校验。
     * @param settlementProfileNo 结算档案编号，用于定位商户、资金账户、币种和日切规则
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 当前方法生成的 {@code ProfileSummary} 结果
     */
    @Override
    public ProfileSummary detail(String settlementProfileNo,
                                 AdminMerchantDataScope dataScope) {
        String profileNo = requiredProfileNo(settlementProfileNo);
        AdminMerchantDataScope scope = requiredScope(dataScope);
        return transactionLogicalReadExecutor.read(() -> requireProfile(profileNo, scope));
    }

    /**
     * CAS 更新只改变后续调度参数；事务与动态数据源在同一代理边界建立，避免写入默认库。
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public ProfileSummary update(String settlementProfileNo,
                                 ProfileUpdateRequest request,
                                 AdminMerchantDataScope dataScope) {
        String profileNo = requiredProfileNo(settlementProfileNo);
        ProfileUpdateRequest command = normalizeUpdate(request);
        AdminMerchantDataScope scope = requiredScope(dataScope);
        if (scope.empty()) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "结算档案不存在");
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("settlementProfileNo", profileNo)
                .addValue("processingMode", command.getProcessingMode())
                .addValue("businessTimeZone", command.getBusinessTimeZone())
                .addValue("dailyCutoffTime", command.getDailyCutoffTime())
                .addValue("expectedVersion", command.getExpectedVersion())
                .addValue("permittedMerchantIds", scope.merchantIds());
        int updated = jdbcTemplate.update("""
                UPDATE merchant_settlement_profile
                SET processing_mode = :processingMode,
                    business_time_zone = :businessTimeZone,
                    daily_cutoff_time = :dailyCutoffTime,
                    version = version + 1,
                    update_time = CURRENT_TIMESTAMP(3)
                WHERE settlement_profile_no = :settlementProfileNo
                  AND version = :expectedVersion
                """ + updateMerchantScopeSql(scope), parameters);
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "结算档案已被其他操作员修改或不在当前数据范围，请刷新后重试");
        }
        return requireProfile(profileNo, scope);
    }

    private PageResult<ProfileSummary> searchNormalized(ProfileSearchRequest query,
                                                        AdminMerchantDataScope dataScope) {
        if (dataScope.empty()) {
            return PageResult.of(0L, query.getPageNo(), query.getPageSize(), List.of());
        }
        String fromSql = fromSql();
        String whereSql = whereSql(query, dataScope);
        MapSqlParameterSource parameters = parameters(query, dataScope);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " + fromSql + whereSql, parameters, Long.class);
        long total = count == null ? 0L : count;
        long offset = (query.getPageNo() - 1L) * query.getPageSize();
        List<ProfileSummary> records = offset < total
                ? jdbcTemplate.query("SELECT " + PROFILE_COLUMNS + fromSql + whereSql + "\n" + """
                        ORDER BY profile.profile_status = 'ACTIVE' DESC, profile.id DESC
                        LIMIT :offset, :limit
                        """, new MapSqlParameterSource(parameters.getValues())
                        .addValue("offset", offset)
                        .addValue("limit", query.getPageSize()),
                BeanPropertyRowMapper.newInstance(ProfileSummary.class))
                : List.of();
        return PageResult.of(total, query.getPageNo(), query.getPageSize(), records);
    }

    private ProfileSummary requireProfile(String settlementProfileNo,
                                          AdminMerchantDataScope dataScope) {
        if (dataScope.empty()) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "结算档案不存在");
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("settlementProfileNo", settlementProfileNo)
                .addValue("permittedMerchantIds", dataScope.merchantIds());
        List<ProfileSummary> rows = jdbcTemplate.query(
                "SELECT " + PROFILE_COLUMNS + fromSql() + """
                        WHERE profile.settlement_profile_no = :settlementProfileNo
                        """ + merchantScopeSql(dataScope) + " LIMIT 1",
                parameters, BeanPropertyRowMapper.newInstance(ProfileSummary.class));
        if (rows.isEmpty()) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "结算档案不存在");
        }
        return rows.get(0);
    }

    private String fromSql() {
        return """
                 FROM merchant_settlement_profile profile
                 LEFT JOIN base_merchant_info merchant
                   ON merchant.merchant_id = profile.merchant_id AND merchant.deleted = 0
                 LEFT JOIN merchant_fund_account account
                   ON account.id = profile.settlement_account_id
                  AND account.merchant_id = profile.merchant_id AND account.deleted = 0
                """;
    }

    private String whereSql(ProfileSearchRequest query, AdminMerchantDataScope dataScope) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        sql.append(merchantScopeSql(dataScope));
        appendCondition(sql, query.getSettlementProfileNo(),
                " AND profile.settlement_profile_no = :settlementProfileNo");
        appendCondition(sql, query.getMerchantId(), " AND profile.merchant_id = :merchantId");
        appendCondition(sql, query.getTargetCurrency(), " AND profile.target_currency = :targetCurrency");
        appendCondition(sql, query.getProcessingMode(), " AND profile.processing_mode = :processingMode");
        appendCondition(sql, query.getProfileStatus(), " AND profile.profile_status = :profileStatus");
        return sql.toString();
    }

    private MapSqlParameterSource parameters(ProfileSearchRequest query,
                                             AdminMerchantDataScope dataScope) {
        return new MapSqlParameterSource()
                .addValue("settlementProfileNo", query.getSettlementProfileNo())
                .addValue("merchantId", query.getMerchantId())
                .addValue("targetCurrency", query.getTargetCurrency())
                .addValue("processingMode", query.getProcessingMode())
                .addValue("profileStatus", query.getProfileStatus())
                .addValue("permittedMerchantIds", dataScope.merchantIds());
    }

    private String merchantScopeSql(AdminMerchantDataScope dataScope) {
        return dataScope.allMerchants() ? "" : " AND profile.merchant_id IN (:permittedMerchantIds)";
    }

    private String updateMerchantScopeSql(AdminMerchantDataScope dataScope) {
        return dataScope.allMerchants() ? "" : " AND merchant_id IN (:permittedMerchantIds)";
    }

    private ProfileSearchRequest normalize(ProfileSearchRequest request) {
        ProfileSearchRequest query = request == null ? new ProfileSearchRequest() : request;
        query.setSettlementProfileNo(trimToNull(query.getSettlementProfileNo()));
        query.setMerchantId(trimToNull(query.getMerchantId()));
        query.setTargetCurrency(upper(query.getTargetCurrency()));
        query.setProcessingMode(upper(query.getProcessingMode()));
        query.setProfileStatus(upper(query.getProfileStatus()));
        if (query.getProcessingMode() != null && !PROCESSING_MODES.contains(query.getProcessingMode())) {
            throw invalid("结算处理模式不正确");
        }
        if (query.getProfileStatus() != null && !PROFILE_STATUSES.contains(query.getProfileStatus())) {
            throw invalid("结算档案状态不正确");
        }
        int pageNo = query.getPageNo() == null ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null ? DEFAULT_PAGE_SIZE : query.getPageSize();
        if (pageNo < 1 || pageSize < 1 || pageSize > 100
                || (long) pageNo * pageSize > maxResultRows) {
            throw invalid("分页参数超出允许范围");
        }
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return query;
    }

    private ProfileUpdateRequest normalizeUpdate(ProfileUpdateRequest request) {
        if (request == null) {
            throw invalid("结算档案修改请求不能为空");
        }
        request.setProcessingMode(upper(request.getProcessingMode()));
        request.setBusinessTimeZone(trimToNull(request.getBusinessTimeZone()));
        if (!PROCESSING_MODES.contains(request.getProcessingMode())) {
            throw invalid("结算处理模式不正确");
        }
        if (request.getBusinessTimeZone() == null) {
            throw invalid("业务时区不能为空");
        }
        try {
            ZoneId.of(request.getBusinessTimeZone());
        } catch (DateTimeException exception) {
            throw invalid("业务时区必须是有效的 IANA 时区");
        }
        if (request.getDailyCutoffTime() == null) {
            throw invalid("日切时间不能为空");
        }
        if (request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            throw invalid("档案版本不正确");
        }
        return request;
    }

    private AdminMerchantDataScope requiredScope(AdminMerchantDataScope dataScope) {
        if (dataScope == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return dataScope;
    }

    private String requiredProfileNo(String value) {
        String profileNo = trimToNull(value);
        if (profileNo == null || profileNo.length() > 64) {
            throw invalid("结算档案号不正确");
        }
        return profileNo;
    }

    private void appendCondition(StringBuilder sql, String value, String condition) {
        if (value != null) {
            sql.append(condition);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
