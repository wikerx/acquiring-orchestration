package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionAmountSummaryResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSummaryResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPaymentMethodSummaryResponse;
import com.scott.payment.merchant.service.MerchantTransactionQueryService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantTransactionQueryService
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户后台交易只读查询实现，仅访问 ShardingSphere 交易逻辑表，并在主查询和富化查询中强制 merchant_id 与分片时间。
 * @status : create
 */
@Service
public class JdbcMerchantTransactionQueryService implements MerchantTransactionQueryService {

    /**
     * TRANSACTION ORDER TABLE，用于保存 Jdbc Merchant Transaction Query Service 中与 交易订单table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_ORDER_TABLE = "transaction_order";
    /**
     * TRANSACTION OPERATION TABLE，用于保存 Jdbc Merchant Transaction Query Service 中与 交易动作table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_OPERATION_TABLE = "transaction_operation";
    /**
     * TRANSACTION PAYMENT METHOD INFO TABLE，表示支付方式、通知方式或调用方式。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_PAYMENT_METHOD_INFO_TABLE = "transaction_payment_method_info";
    /**
     * DEFAULT QUERY TIME ZONE，用于保存 Jdbc Merchant Transaction Query Service 中与 defaultquerytimezone 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_QUERY_TIME_ZONE = "Asia/Shanghai";

    /**
     * jdbc Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;
    /** 在 transaction 逻辑数据源上执行普通读和强一致读。 */
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    /** 单次同步查询允许返回的最大记录数。 */
    private final int maxResultRows;
    /** 当前版本已登记物理节点中的最早季度，用于受控批量定位生命周期主单。 */
    private final LocalDateTime registeredNodeBegin;

    /**
     * 创建商户交易只读查询实现。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     */
    public JdbcMerchantTransactionQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new TransactionLogicalReadExecutor(),
                new TransactionShardingProperties());
    }

    /**
     * 创建生产环境商户交易查询服务，并为每条 JDBC Statement 应用同步查询超时。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 查询资源预算配置
     * @param queryJdbcTemplateFactory 查询专用 JDBC 模板工厂
     */
    @Autowired
    public JdbcMerchantTransactionQueryService(DataSource dataSource,
                                               TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                               TransactionShardingProperties shardingProperties,
                                               TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                transactionLogicalReadExecutor, shardingProperties);
    }

    /**
     * 创建同时执行商户隔离、逻辑路由和结果行数预算的交易查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @param transactionLogicalReadExecutor 交易逻辑数据源只读执行器
     * @param shardingProperties 查询资源预算配置
     */
    public JdbcMerchantTransactionQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                               TransactionLogicalReadExecutor transactionLogicalReadExecutor,
                                               TransactionShardingProperties shardingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
        this.maxResultRows = shardingProperties.getQueryBudget().getMaxResultRows();
        this.registeredNodeBegin = resolveRegisteredNodeBegin(shardingProperties.getPhysicalNodes());
    }

    /**
     * 分页查询当前商户交易生命周期主单。
     *
     * @param query 已注入当前登录商户号的查询条件
     * @return 主单分页结果
     */
    @Override
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        return executeRead(false, () -> pageOrdersNormalized(safeQuery));
    }

    /**
     * 按已注入的 merchant_id 和分片时间范围执行分页，任何 SQL 分支均不得移除商户谓词。
     *
     * @param safeQuery 已校验且 merchantId 非空的查询
     * @return 仅包含当前商户数据的全季度分页结果
     */
    private PageResult<TransactionOrderResponse> pageOrdersNormalized(TransactionPageQuery safeQuery) {
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        long total = countOrders(TRANSACTION_ORDER_TABLE, safeQuery);
        List<TransactionOrderResponse> rows = offset < total
                ? selectOrders(TRANSACTION_ORDER_TABLE, safeQuery, offset, limit)
                : List.of();
        enrichOrders(rows);
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    /**
     * 分页查询当前商户交易动作单并聚合统计。
     *
     * @param query 已注入当前登录商户号的查询条件
     * @return 动作单分页与统计
     */
    @Override
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = normalize(query);
        return executeRead(false, () -> {
            TransactionOperationSearchResponse response = new TransactionOperationSearchResponse();
            response.setPage(pageOperations(safeQuery));
            response.setSummary(operationSummary(safeQuery));
            return response;
        });
    }

    /**
     * 查询当前商户交易聚合详情。
     *
     * @param merchantId 当前登录商户号
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 列表查询返回的真实交易分片时间
     * @return 商户可见交易详情
     */
    @Override
    public TransactionDetailResponse detail(String merchantId,
                                            String transactionId,
                                            LocalDateTime transactionDateTime,
                                            LocalDateTime rootTransactionDateTime) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        if (!StringUtils.hasText(transactionId)
                || transactionDateTime == null
                || rootTransactionDateTime == null) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        return executeRead(true,
                () -> detailNormalized(
                        merchantId, transactionId, transactionDateTime, rootTransactionDateTime));
    }

    /**
     * 在主库读作用域装配商户交易详情，并在动作单和主单两层重复校验 merchant_id。
     *
     * @param merchantId 当前登录商户号
     * @param transactionId 平台交易号
     * @param transactionDateTime 列表查询返回的真实交易分片时间
     * @return 当前商户可见的聚合详情
     */
    private TransactionDetailResponse detailNormalized(String merchantId,
                                                        String transactionId,
                                                        LocalDateTime transactionDateTime,
                                                        LocalDateTime rootTransactionDateTime) {
        TransactionOperationResponse sourceOperation = selectOperationByTransactionId(
                TRANSACTION_OPERATION_TABLE, transactionId, transactionDateTime, merchantId);
        if (sourceOperation == null || !merchantId.equals(sourceOperation.getMerchantId())) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOrderResponse order = selectOrderByOperationId(
                TRANSACTION_ORDER_TABLE, sourceOperation.getOperationId(), rootTransactionDateTime, merchantId);
        if (order == null || !merchantId.equals(order.getMerchantId())) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        LocalDateTime beginTime = order.getTransactionDateTime() == null ? sourceOperation.getTransactionDateTime() : order.getTransactionDateTime();
        List<TransactionOperationResponse> operations = selectOperationsByOperationId(
                sourceOperation.getOperationId(), merchantId, beginTime, LocalDateTime.now());
        operations.sort(Comparator.comparing(TransactionOperationResponse::getOperationSequence, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TransactionOperationResponse::getOperationTime, Comparator.nullsLast(LocalDateTime::compareTo)));
        enrichOperations(operations);
        enrichOrders(List.of(order));
        TransactionDetailResponse detail = new TransactionDetailResponse();
        detail.setOrder(order);
        detail.setOperations(operations);
        return detail;
    }

    /**
     * 通过交易逻辑表分页查询当前商户的交易操作单，由 ShardingSphere 完成跨季度路由和归并。
     *
     * @param safeQuery 已校验并绑定 merchantId 和时间范围的查询
     * @return 跨季度统一分页结果
     */
    private PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery safeQuery) {
        long offset = offset(safeQuery);
        long limit = safeQuery.safePageSize();
        long total = countOperations(
                TRANSACTION_OPERATION_TABLE, TRANSACTION_PAYMENT_METHOD_INFO_TABLE, safeQuery);
        List<TransactionOperationResponse> rows = offset < total
                ? selectOperations(
                        TRANSACTION_OPERATION_TABLE,
                        TRANSACTION_PAYMENT_METHOD_INFO_TABLE,
                        safeQuery,
                        offset,
                        limit)
                : List.of();
        enrichOperations(rows);
        return PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), rows);
    }

    /**
     * 通过交易逻辑表汇总当前商户交易操作金额和支付方式。
     * 金额保持数据库 {@code BigDecimal} 精度，并始终按币种分别汇总。
     *
     * @param safeQuery 已校验并绑定商户和时间范围的查询
     * @return 跨季度操作统计
     */
    private TransactionOperationSummaryResponse operationSummary(TransactionPageQuery safeQuery) {
        SummaryAccumulator accumulator = new SummaryAccumulator();
        selectAmountSummary(
                TRANSACTION_OPERATION_TABLE,
                TRANSACTION_PAYMENT_METHOD_INFO_TABLE,
                safeQuery).forEach(accumulator::addAmount);
        selectPaymentMethodSummary(
                TRANSACTION_OPERATION_TABLE,
                TRANSACTION_PAYMENT_METHOD_INFO_TABLE,
                safeQuery).forEach(accumulator::addPaymentMethod);
        return accumulator.toResponse();
    }

    /**
     * 统计交易主单，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long countOrders(String table, TransactionPageQuery query) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s
                WHERE deleted = 0
                  AND merchant_id = :merchantId
                  AND transaction_date_time >= :beginTime
                  AND transaction_date_time < :endTime
                %s
                """.formatted(table, orderWhereSql(query)), orderParams(query), Long.class);
    }

    /**
     * 查询交易主单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @param offset 分页或扫描窗口参数，用于限制单次查询范围
     * @param limit 分页或扫描窗口参数，用于限制单次查询范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<TransactionOrderResponse> selectOrders(String table, TransactionPageQuery query, long offset, long limit) {
        MapSqlParameterSource params = orderParams(query)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbcTemplate.query("""
                SELECT *
                FROM %s
                WHERE deleted = 0
                  AND merchant_id = :merchantId
                  AND transaction_date_time >= :beginTime
                  AND transaction_date_time < :endTime
                %s
                ORDER BY transaction_date_time DESC, id DESC
                LIMIT :offset, :limit
                """.formatted(table, orderWhereSql(query)), params, orderMapper());
    }

    /**
     * 统计交易动作，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param paymentTable payment Table 输入值，参与 payment表 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long countOperations(String table, String paymentTable, TransactionPageQuery query) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM %s o
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time < :endTime
                %s
                """.formatted(table, operationWhereSql(query, paymentTable)), operationParams(query), Long.class);
    }

    /**
     * 查询交易动作，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param paymentTable payment Table 输入值，参与 paymenttable 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @param offset 分页或扫描窗口参数，用于限制单次查询范围
     * @param limit 分页或扫描窗口参数，用于限制单次查询范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<TransactionOperationResponse> selectOperations(String table, String paymentTable, TransactionPageQuery query, long offset, long limit) {
        MapSqlParameterSource params = operationParams(query)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbcTemplate.query("""
                SELECT o.*
                FROM %s o
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time < :endTime
                %s
                ORDER BY o.transaction_date_time DESC, o.id DESC
                LIMIT :offset, :limit
                """.formatted(table, operationWhereSql(query, paymentTable)), params, operationMapper());
    }

    /**
     * 查询金额汇总，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param paymentTable payment Table 输入值，参与 paymenttable 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<SummaryRow> selectAmountSummary(String table, String paymentTable, TransactionPageQuery query) {
        return jdbcTemplate.query("""
                SELECT o.transaction_status AS transaction_status,
                       COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
                       o.currency_exponent AS currency_exponent,
                       COUNT(1) AS count,
                       COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount
                FROM %s o
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time < :endTime
                %s
                GROUP BY o.transaction_status, COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
                """.formatted(table, operationWhereSql(query, paymentTable)), operationParams(query), summaryRowMapper());
    }

    /**
     * 查询支付方式汇总，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param paymentTable payment Table 输入值，参与 paymenttable 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<SummaryRow> selectPaymentMethodSummary(String table, String paymentTable, TransactionPageQuery query) {
        return jdbcTemplate.query("""
                SELECT COALESCE(p.payment_method, 'UNKNOWN') AS payment_method,
                       p.payment_brand AS payment_brand,
                       COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
                       o.currency_exponent AS currency_exponent,
                       COUNT(1) AS count,
                       COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount
                FROM %s o
                LEFT JOIN %s p ON p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time
                WHERE o.deleted = 0
                  AND o.merchant_id = :merchantId
                  AND o.transaction_date_time >= :beginTime
                  AND o.transaction_date_time < :endTime
                %s
                GROUP BY COALESCE(p.payment_method, 'UNKNOWN'), p.payment_brand, COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
                """.formatted(table, paymentTable, operationWhereSql(query, paymentTable)), operationParams(query), paymentSummaryRowMapper());
    }

    /**
     * 整理订单wheresql，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String orderWhereSql(TransactionPageQuery query) {
        StringBuilder sql = new StringBuilder();
        appendTextFilter(sql, query.getMerchantOrderNo(), "AND merchant_order_no = :merchantOrderNo");
        appendTextFilter(sql, query.getTransactionId(), "AND (root_transaction_id = :transactionId OR latest_transaction_id = :transactionId)");
        appendTextFilter(sql, query.getSourceTransactionId(), "AND source_transaction_id = :sourceTransactionId");
        appendTextFilter(sql, query.getTransactionType(), "AND transaction_type = :transactionType");
        appendTextFilter(sql, query.getTransactionStatus(), "AND transaction_status = :transactionStatus");
        appendTextFilter(sql, query.getPaymentMethod(), "AND payment_method = :paymentMethod");
        appendTextFilter(sql, query.getPaymentBrand(), "AND payment_brand = :paymentBrand");
        appendTextFilter(sql, query.getChannelOrderNo(), "AND channel_order_no = :channelOrderNo");
        appendTextFilter(sql, query.getChannelMatchStatus(), "AND channel_match_status = :channelMatchStatus");
        appendTextFilter(sql, query.getReconciliationStatus(), "AND reconciliation_status = :reconciliationStatus");
        appendTextFilter(sql, query.getSettlementStatus(), "AND settlement_status = :settlementStatus");
        return sql.toString();
    }

    /**
     * 整理动作wheresql，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @param paymentTable payment Table 输入值，参与 payment表 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String operationWhereSql(TransactionPageQuery query, String paymentTable) {
        StringBuilder sql = new StringBuilder();
        appendTextFilter(sql, query.getMerchantOrderNo(), "AND o.merchant_order_no = :merchantOrderNo");
        appendTextFilter(sql, query.getTransactionId(), "AND o.transaction_id = :transactionId");
        appendTextFilter(sql, query.getSourceTransactionId(), "AND o.source_transaction_id = :sourceTransactionId");
        appendTextFilter(sql, query.getTransactionType(), "AND o.transaction_type = :transactionType");
        appendTextFilter(sql, query.getTransactionStatus(), "AND o.transaction_status = :transactionStatus");
        appendTextFilter(sql, query.getChannelOrderNo(), "AND o.channel_order_no = :channelOrderNo");
        appendTextFilter(sql, query.getChannelMatchStatus(), "AND o.channel_match_status = :channelMatchStatus");
        appendTextFilter(sql, query.getReconciliationStatus(), "AND o.reconciliation_status = :reconciliationStatus");
        appendTextFilter(sql, query.getSettlementStatus(), "AND o.settlement_status = :settlementStatus");
        if (StringUtils.hasText(query.getPaymentMethod())) {
            sql.append(" AND EXISTS (SELECT 1 FROM ").append(paymentTable)
                    .append(" p WHERE p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time AND p.payment_method = :paymentMethod)");
        }
        if (StringUtils.hasText(query.getPaymentBrand())) {
            sql.append(" AND EXISTS (SELECT 1 FROM ").append(paymentTable)
                    .append(" p WHERE p.transaction_id = o.transaction_id AND p.transaction_date_time = o.transaction_date_time AND p.payment_brand = :paymentBrand)");
        }
        return sql.toString();
    }

    /**
     * 构造文本筛选对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param sql sql 输入值，参与 sql 的查询、校验、转换、写入或日志摘要
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param fragment fragment 输入值，参与 fragment 的查询、校验、转换、写入或日志摘要
     */
    private void appendTextFilter(StringBuilder sql, String value, String fragment) {
        if (StringUtils.hasText(value)) {
            sql.append(' ').append(fragment);
        }
    }

    /**
     * 整理订单参数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource orderParams(TransactionPageQuery query) {
        return baseParams(query)
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("transactionId", query.getTransactionId())
                .addValue("sourceTransactionId", query.getSourceTransactionId())
                .addValue("transactionType", query.getTransactionType())
                .addValue("transactionStatus", query.getTransactionStatus())
                .addValue("paymentMethod", query.getPaymentMethod())
                .addValue("paymentBrand", query.getPaymentBrand())
                .addValue("channelOrderNo", query.getChannelOrderNo())
                .addValue("channelMatchStatus", query.getChannelMatchStatus())
                .addValue("reconciliationStatus", query.getReconciliationStatus())
                .addValue("settlementStatus", query.getSettlementStatus());
    }

    /**
     * 整理动作参数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource operationParams(TransactionPageQuery query) {
        return baseParams(query)
                .addValue("merchantOrderNo", query.getMerchantOrderNo())
                .addValue("transactionId", query.getTransactionId())
                .addValue("sourceTransactionId", query.getSourceTransactionId())
                .addValue("transactionType", query.getTransactionType())
                .addValue("transactionStatus", query.getTransactionStatus())
                .addValue("channelOrderNo", query.getChannelOrderNo())
                .addValue("channelMatchStatus", query.getChannelMatchStatus())
                .addValue("reconciliationStatus", query.getReconciliationStatus())
                .addValue("settlementStatus", query.getSettlementStatus())
                .addValue("paymentMethod", query.getPaymentMethod())
                .addValue("paymentBrand", query.getPaymentBrand());
    }

    /**
     * 整理基础 SQL 参数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MapSqlParameterSource baseParams(TransactionPageQuery query) {
        return new MapSqlParameterSource()
                .addValue("merchantId", query.getMerchantId())
                .addValue("beginTime", query.getBeginTime())
                .addValue("endTime", exclusiveEnd(query.getEndTime()));
    }

    /**
     * 查询按交易号定位的动作单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
     * @param transactionDateTime 列表返回的真实毫秒分片时间
     * @param merchantId 当前登录商户号
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private TransactionOperationResponse selectOperationByTransactionId(String table,
                                                                         String transactionId,
                                                                         LocalDateTime transactionDateTime,
                                                                         String merchantId) {
        List<TransactionOperationResponse> rows = jdbcTemplate.query("""
                SELECT *
                FROM %s
                WHERE transaction_id = :transactionId
                  AND merchant_id = :merchantId
                  AND transaction_date_time = :transactionDateTime
                  AND deleted = 0
                LIMIT 1
                """.formatted(table), new MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("merchantId", merchantId)
                .addValue("transactionDateTime", transactionDateTime),
                operationMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询订单by动作ID，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param table 固定交易逻辑表名，只允许使用类内受控常量
     * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private TransactionOrderResponse selectOrderByOperationId(String table,
                                                               String operationId,
                                                               LocalDateTime transactionDateTime,
                                                               String merchantId) {
        List<TransactionOrderResponse> rows = jdbcTemplate.query("""
                SELECT *
                FROM %s
                WHERE operation_id = :operationId
                  AND merchant_id = :merchantId
                  AND transaction_date_time = :transactionDateTime
                  AND deleted = 0
                LIMIT 1
                """.formatted(table), new MapSqlParameterSource()
                .addValue("operationId", operationId)
                .addValue("merchantId", merchantId)
                .addValue("transactionDateTime", transactionDateTime), orderMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询按操作号定位的动作单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param operationId 平台操作号，用于定位单次授权、请款、退款、撤销或通知动作
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<TransactionOperationResponse> selectOperationsByOperationId(String operationId,
                                                                              String merchantId,
                                                                              LocalDateTime beginTime,
                                                                              LocalDateTime endTime) {
        return jdbcTemplate.query("""
                    SELECT *
                    FROM %s
                    WHERE operation_id = :operationId
                      AND merchant_id = :merchantId
                      AND transaction_date_time >= :beginTime
                      AND transaction_date_time < :endTime
                      AND deleted = 0
                    ORDER BY operation_sequence ASC, operation_time ASC
                    """.formatted(TRANSACTION_OPERATION_TABLE), new MapSqlParameterSource()
                    .addValue("operationId", operationId)
                    .addValue("merchantId", merchantId)
                    .addValue("beginTime", beginTime)
                    .addValue("endTime", exclusiveEnd(endTime)), operationMapper());
    }

    /**
     * 构造交易动作对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     */
    private void enrichOperations(List<TransactionOperationResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, PaymentInfoRow> paymentInfoByTransaction = paymentInfoByTransaction(rows);
        Map<String, TransactionOrderResponse> orderByOperation = orderByOperation(rows);
        for (TransactionOperationResponse row : rows) {
            PaymentInfoRow info = paymentInfoByTransaction.get(row.getTransactionId());
            if (info != null) {
                row.setPaymentMethod(info.paymentMethod());
                row.setPaymentBrand(info.paymentBrand());
                row.setCardBin(info.cardBin());
                row.setCardNumberMasked(info.cardNumberMasked());
            }
            TransactionOrderResponse order = orderByOperation.get(row.getOperationId());
            if (order != null) {
                row.setRootTransactionDateTime(order.getTransactionDateTime());
                row.setAuthorizedAmount(order.getAuthorizedAmount());
                row.setCapturedAmount(order.getCapturedAmount());
                row.setRefundedAmount(order.getRefundedAmount());
                row.setAvailableCaptureAmount(order.getAvailableCaptureAmount());
                row.setAvailableRefundAmount(order.getAvailableRefundAmount());
                row.setMerchantResponseMessage(resolveMerchantResponseMessage(
                        row.getTransactionStatus(),
                        order.getMerchantResponseMessage()));
            }
            row.setAccessType("DIRECT_API");
        }
    }

    /**
     * 构造交易主单对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     */
    private void enrichOrders(List<TransactionOrderResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, PaymentInfoRow> paymentInfoByTransaction = paymentInfoByOrderTransaction(rows);
        Map<String, PaymentInfoRow> paymentInfoByOperation = paymentInfoByOrderOperation(rows);
        Map<String, OperationVisibleInfoRow> operationInfoByOperation = operationVisibleInfoByOperation(rows);
        for (TransactionOrderResponse row : rows) {
            PaymentInfoRow info = firstPaymentInfo(row, paymentInfoByTransaction);
            if (!hasCardInfo(info)) {
                info = paymentInfoByOperation.get(row.getOperationId());
            }
            if (info != null) {
                row.setPaymentMethod(info.paymentMethod());
                row.setPaymentBrand(info.paymentBrand());
                row.setCardBin(info.cardBin());
                row.setCardNumberMasked(info.cardNumberMasked());
            }
            OperationVisibleInfoRow operationInfo = operationInfoByOperation.get(row.getOperationId());
            if (operationInfo != null) {
                row.setAuthCode(operationInfo.authCode());
                if (!StringUtils.hasText(row.getCardBin())) {
                    row.setCardBin(operationInfo.cardBin());
                }
                if (!StringUtils.hasText(row.getCardNumberMasked())) {
                    row.setCardNumberMasked(operationInfo.cardNumberMasked());
                }
            }
        }
    }

    /**
     * 整理首个paymentinfo，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param paymentInfoByTransaction payment Info By Transaction 输入值，参与 paymentinfoby交易 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private PaymentInfoRow firstPaymentInfo(TransactionOrderResponse row, Map<String, PaymentInfoRow> paymentInfoByTransaction) {
        PaymentInfoRow latest = paymentInfoByTransaction.get(row.getLatestTransactionId());
        if (hasCardInfo(latest)) {
            return latest;
        }
        PaymentInfoRow root = paymentInfoByTransaction.get(row.getRootTransactionId());
        if (hasCardInfo(root)) {
            return root;
        }
        return latest != null ? latest : root;
    }

    /**
     * 判断 has card info 条件是否成立，用于控制 Jdbc Merchant Transaction Query Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasCardInfo(PaymentInfoRow row) {
        return row != null && (StringUtils.hasText(row.cardBin()) || StringUtils.hasText(row.cardNumberMasked()));
    }

    /**
     * 整理按交易号查询的支付工具信息，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, PaymentInfoRow> paymentInfoByTransaction(List<TransactionOperationResponse> rows) {
        Map<String, PaymentInfoRow> result = new LinkedHashMap<>();
        Map<MerchantTimeScope, List<String>> idsByScope = new LinkedHashMap<>();
        for (TransactionOperationResponse row : rows) {
            if (row.getTransactionDateTime() != null
                    && StringUtils.hasText(row.getMerchantId())
                    && StringUtils.hasText(row.getTransactionId())) {
                MerchantTimeScope scope = new MerchantTimeScope(row.getMerchantId(), row.getTransactionDateTime());
                idsByScope.computeIfAbsent(scope, key -> new ArrayList<>()).add(row.getTransactionId());
            }
        }
        idsByScope.forEach((scope, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            jdbcTemplate.query("""
                    SELECT p.transaction_id, p.operation_id, p.payment_method, p.payment_brand,
                           p.card_bin, p.card_last4, p.card_number_masked
                    FROM %s p
                    WHERE p.transaction_id IN (:transactionIds)
                      AND p.transaction_date_time = :transactionDateTime
                      AND EXISTS (
                          SELECT 1
                          FROM %s o
                          WHERE o.transaction_id = p.transaction_id
                            AND o.transaction_date_time = p.transaction_date_time
                            AND o.merchant_id = :merchantId
                            AND o.deleted = 0
                      )
                    """.formatted(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, TRANSACTION_OPERATION_TABLE),
                    new MapSqlParameterSource()
                    .addValue("transactionIds", ids)
                    .addValue("transactionDateTime", scope.transactionDateTime())
                    .addValue("merchantId", scope.merchantId()), paymentInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.transactionId(), row));
        });
        return result;
    }

    /**
     * 整理按订单交易号查询的支付工具信息，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, PaymentInfoRow> paymentInfoByOrderTransaction(List<TransactionOrderResponse> rows) {
        Map<String, PaymentInfoRow> result = new LinkedHashMap<>();
        Map<MerchantTimeScope, List<String>> idsByScope = new LinkedHashMap<>();
        for (TransactionOrderResponse row : rows) {
            if (row.getTransactionDateTime() == null || !StringUtils.hasText(row.getMerchantId())) {
                continue;
            }
            MerchantTimeScope scope = new MerchantTimeScope(row.getMerchantId(), row.getTransactionDateTime());
            List<String> transactionIds = idsByScope.computeIfAbsent(scope, key -> new ArrayList<>());
            addIfText(transactionIds, row.getLatestTransactionId());
            addIfText(transactionIds, row.getRootTransactionId());
        }
        idsByScope.forEach((scope, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            jdbcTemplate.query("""
                    SELECT p.transaction_id, p.operation_id, p.payment_method, p.payment_brand,
                           p.card_bin, p.card_last4, p.card_number_masked
                    FROM %s p
                    WHERE p.transaction_date_time = :transactionDateTime
                      AND p.transaction_id IN (:transactionIds)
                      AND EXISTS (
                          SELECT 1
                          FROM %s o
                          WHERE o.transaction_id = p.transaction_id
                            AND o.transaction_date_time = p.transaction_date_time
                            AND o.merchant_id = :merchantId
                            AND o.deleted = 0
                      )
                    """.formatted(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, TRANSACTION_OPERATION_TABLE),
                    new MapSqlParameterSource()
                    .addValue("transactionDateTime", scope.transactionDateTime())
                    .addValue("transactionIds", ids)
                    .addValue("merchantId", scope.merchantId()), paymentInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.transactionId(), row));
        });
        return result;
    }

    /**
     * 整理按订单操作号查询的支付工具信息，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, PaymentInfoRow> paymentInfoByOrderOperation(List<TransactionOrderResponse> rows) {
        Map<String, PaymentInfoRow> result = new LinkedHashMap<>();
        Map<MerchantTimeScope, List<String>> operationIdsByScope = new LinkedHashMap<>();
        for (TransactionOrderResponse row : rows) {
            if (row.getTransactionDateTime() != null
                    && StringUtils.hasText(row.getMerchantId())
                    && StringUtils.hasText(row.getOperationId())) {
                MerchantTimeScope scope = new MerchantTimeScope(row.getMerchantId(), row.getTransactionDateTime());
                operationIdsByScope.computeIfAbsent(scope, key -> new ArrayList<>()).add(row.getOperationId());
            }
        }
        operationIdsByScope.forEach((scope, operationIds) -> {
            if (operationIds.isEmpty()) {
                return;
            }
            jdbcTemplate.query("""
                    SELECT p.transaction_id, p.operation_id, p.payment_method, p.payment_brand,
                           p.card_bin, p.card_last4, p.card_number_masked
                    FROM %s p
                    WHERE p.operation_id IN (:operationIds)
                      AND p.transaction_date_time = :transactionDateTime
                      AND EXISTS (
                          SELECT 1
                          FROM %s o
                          WHERE o.operation_id = p.operation_id
                            AND o.transaction_date_time = p.transaction_date_time
                            AND o.merchant_id = :merchantId
                            AND o.deleted = 0
                      )
                    ORDER BY p.transaction_date_time ASC, p.id ASC
                    """.formatted(TRANSACTION_PAYMENT_METHOD_INFO_TABLE, TRANSACTION_OPERATION_TABLE),
                    new MapSqlParameterSource()
                    .addValue("operationIds", operationIds)
                    .addValue("transactionDateTime", scope.transactionDateTime())
                    .addValue("merchantId", scope.merchantId()), paymentInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.operationId(), row));
        });
        return result;
    }

    /**
     * 整理动作可见信息按动作，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, OperationVisibleInfoRow> operationVisibleInfoByOperation(List<TransactionOrderResponse> rows) {
        Map<String, OperationVisibleInfoRow> result = new LinkedHashMap<>();
        Map<MerchantTimeScope, List<String>> operationIdsByScope = new LinkedHashMap<>();
        for (TransactionOrderResponse row : rows) {
            if (row.getTransactionDateTime() != null
                    && StringUtils.hasText(row.getMerchantId())
                    && StringUtils.hasText(row.getOperationId())) {
                MerchantTimeScope scope = new MerchantTimeScope(row.getMerchantId(), row.getTransactionDateTime());
                operationIdsByScope.computeIfAbsent(scope, key -> new ArrayList<>()).add(row.getOperationId());
            }
        }
        operationIdsByScope.forEach((scope, operationIds) -> {
            if (operationIds.isEmpty()) {
                return;
            }
            jdbcTemplate.query("""
                    SELECT o.operation_id,
                           MAX(NULLIF(o.auth_code, '')) AS auth_code,
                           MAX(NULLIF(p.card_bin, '')) AS card_bin,
                           MAX(NULLIF(p.card_last4, '')) AS card_last4,
                           MAX(NULLIF(p.card_number_masked, '')) AS card_number_masked
                    FROM %s o
                    LEFT JOIN %s p ON p.operation_id = o.operation_id
                                      AND p.transaction_date_time = o.transaction_date_time
                    WHERE o.operation_id IN (:operationIds)
                      AND o.transaction_date_time = :transactionDateTime
                      AND o.merchant_id = :merchantId
                      AND o.deleted = 0
                    GROUP BY o.operation_id
                    """.formatted(TRANSACTION_OPERATION_TABLE, TRANSACTION_PAYMENT_METHOD_INFO_TABLE),
                    new MapSqlParameterSource()
                    .addValue("operationIds", operationIds)
                    .addValue("transactionDateTime", scope.transactionDateTime())
                    .addValue("merchantId", scope.merchantId()), operationVisibleInfoMapper())
                    .forEach(row -> result.putIfAbsent(row.operationId(), row));
        });
        return result;
    }

    /**
     * 创建if文本，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 商户后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param values values 输入值，参与 values 的查询、校验、转换、写入或日志摘要
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void addIfText(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    /**
     * 整理订单按动作，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, TransactionOrderResponse> orderByOperation(List<TransactionOperationResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        String merchantId = rows.stream()
                .map(TransactionOperationResponse::getMerchantId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing"));
        List<String> operationIds = rows.stream()
                .map(TransactionOperationResponse::getOperationId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (operationIds.isEmpty()) {
            return Map.of();
        }
        List<TransactionOrderResponse> orders = jdbcTemplate.query("""
                SELECT *
                FROM transaction_order
                WHERE merchant_id = :merchantId
                  AND operation_id IN (:operationIds)
                  AND transaction_date_time >= :registeredNodeBegin
                  AND transaction_date_time < :registeredNodeEnd
                  AND deleted = 0
                """, new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("operationIds", operationIds)
                .addValue("registeredNodeBegin", registeredNodeBegin)
                .addValue("registeredNodeEnd", exclusiveEnd(LocalDateTime.now())), orderMapper());
        return orders.stream().collect(java.util.stream.Collectors.toMap(
                TransactionOrderResponse::getOperationId,
                java.util.function.Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    /**
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
    private TransactionPageQuery normalize(TransactionPageQuery query) {
        TransactionPageQuery safeQuery = query == null ? new TransactionPageQuery() : query;
        if (!StringUtils.hasText(safeQuery.getMerchantId())) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        fillDefaultTimeRange(safeQuery);
        normalizeMerchantResponseCode(safeQuery);
        safeQuery.setPageSize((int) Math.min(safeQuery.safePageSize(), maxResultRows));
        return safeQuery;
    }

    /**
     * 构造默认时间范围对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     */
    private void fillDefaultTimeRange(TransactionPageQuery query) {
        ZoneId queryZone = resolveQueryZone(query.getQueryTimeZone());
        LocalDateTime safeEnd = query.getEndTime() == null ? LocalDateTime.now(queryZone) : query.getEndTime();
        LocalDateTime safeBegin = query.getBeginTime() == null ? safeEnd.toLocalDate().atStartOfDay() : query.getBeginTime();
        if (safeBegin.isAfter(safeEnd)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "beginTime must not be after endTime");
        }
        query.setBeginTime(convertBetweenZones(safeBegin, queryZone, ZoneId.of(DEFAULT_QUERY_TIME_ZONE)));
        query.setEndTime(convertBetweenZones(safeEnd, queryZone, ZoneId.of(DEFAULT_QUERY_TIME_ZONE)));
        query.setQueryTimeZone(queryZone.getId());
    }

    /**
     * 解析normalize商户响应编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     */
    private void normalizeMerchantResponseCode(TransactionPageQuery query) {
        if (!StringUtils.hasText(query.getMerchantResponseCode())) {
            return;
        }
        String mappedStatus = resolveStatusByMerchantResponseCode(query.getMerchantResponseCode());
        if (!StringUtils.hasText(mappedStatus)) {
            query.setTransactionStatus("__NO_MATCH__");
            return;
        }
        if (StringUtils.hasText(query.getTransactionStatus()) && !Objects.equals(query.getTransactionStatus(), mappedStatus)) {
            query.setTransactionStatus("__NO_MATCH__");
            return;
        }
        query.setTransactionStatus(mappedStatus);
    }

    /**
     * 解析resolve查询zone，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param queryTimeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 构造、转换或解析后的业务值
     */
    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : DEFAULT_QUERY_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "queryTimeZone is invalid");
        }
    }

    /**
     * 解析normalizezoneID，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param zone zone 输入值，参与 zone 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeZoneId(String zone) {
        if (!StringUtils.hasText(zone)) {
            return DEFAULT_QUERY_TIME_ZONE;
        }
        String normalized = zone.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("UTC".equals(upper) || "GMT".equals(upper)) {
            return upper;
        }
        if (upper.startsWith("UTC+") || upper.startsWith("UTC-") || upper.startsWith("GMT+") || upper.startsWith("GMT-")) {
            String prefix = upper.substring(0, 3);
            String offset = upper.substring(3);
            if (offset.matches("[+-]\\d{1,2}")) {
                return prefix + String.format("%+03d:00", Integer.parseInt(offset));
            }
            if (offset.matches("[+-]\\d{1,2}:\\d{2}")) {
                String[] parts = offset.substring(1).split(":");
                return prefix + offset.charAt(0) + String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
            }
        }
        return normalized;
    }

    /**
     * 构造betweenzones对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param sourceTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param sourceZone source Zone 输入值，参与 来源zone 的查询、校验、转换、写入或日志摘要
     * @param targetZone target Zone 输入值，参与 targetzone 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private LocalDateTime convertBetweenZones(LocalDateTime sourceTime, ZoneId sourceZone, ZoneId targetZone) {
        ZonedDateTime source = sourceTime.atZone(sourceZone);
        return source.withZoneSameInstant(targetZone).toLocalDateTime();
    }

    /**
     * 规范化offset，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long offset(PageRequest query) {
        return (query.safePageNo() - 1) * query.safePageSize();
    }

    /**
     * 在 transaction 逻辑数据源执行普通读或主库强一致读。
     */
    private <T> T executeRead(boolean primaryOnly, Supplier<T> query) {
        return primaryOnly
                ? transactionLogicalReadExecutor.readPrimary(query)
                : transactionLogicalReadExecutor.read(query);
    }

    /** 将包含式结束时间转换为 MySQL DATETIME(3) 半开区间上界。 */
    private LocalDateTime exclusiveEnd(LocalDateTime endTime) {
        LocalDateTime actualEnd = endTime == null ? LocalDateTime.now() : endTime;
        return actualEnd.plusNanos(1_000_000L);
    }

    /**
     * 整理订单映射器，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RowMapper<TransactionOrderResponse> orderMapper() {
        return (rs, rowNum) -> {
            TransactionOrderResponse row = new TransactionOrderResponse();
            row.setOperationId(rs.getString("operation_id"));
            row.setRootTransactionId(rs.getString("root_transaction_id"));
            row.setLatestTransactionId(rs.getString("latest_transaction_id"));
            row.setMerchantId(rs.getString("merchant_id"));
            row.setMerchantOrderNo(rs.getString("merchant_order_no"));
            row.setMerchantOrderId(rs.getString("merchant_order_id"));
            row.setPaymentMethod(rs.getString("payment_method"));
            row.setPaymentBrand(rs.getString("payment_brand"));
            row.setCardBin(null);
            row.setCardNumberMasked(null);
            row.setAuthCode(null);
            row.setTransactionType(rs.getString("transaction_type"));
            row.setTransactionStatus(rs.getString("transaction_status"));
            row.setLifecycleStatus(resolveLifecycleStatus(row, rs));
            row.setLifecycleStatusMessage(row.getLifecycleStatus());
            row.setProcessStage(rs.getString("process_stage"));
            row.setLabelCurrency(rs.getString("label_currency"));
            row.setLabelAmount(rs.getBigDecimal("label_amount"));
            row.setTransactionCurrency(rs.getString("transaction_currency"));
            row.setTransactionAmount(rs.getBigDecimal("transaction_amount"));
            row.setCurrentCurrency(row.getTransactionCurrency());
            row.setCurrentAmount(resolveCurrentAmount(
                    row.getTransactionType(),
                    row.getTransactionAmount(),
                    rs.getBigDecimal("authorized_amount")));
            row.setCurrencyExponent(nullableInt(rs, "currency_exponent"));
            row.setTransactionRate(defaultRate(rs.getBigDecimal("transaction_rate")));
            row.setDccEnabled(nullableInt(rs, "dcc_enabled"));
            row.setEdcEnabled(nullableInt(rs, "edc_enabled"));
            row.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
            row.setMerchantResponseMessage(resolveMerchantResponseMessage(
                    row.getTransactionStatus(),
                    rs.getString("merchant_visible_message")));
            row.setAuthorizedAmount(rs.getBigDecimal("authorized_amount"));
            row.setCapturedAmount(rs.getBigDecimal("captured_amount"));
            row.setRefundedAmount(rs.getBigDecimal("refunded_amount"));
            row.setAvailableCaptureAmount(rs.getBigDecimal("available_capture_amount"));
            row.setAvailableRefundAmount(rs.getBigDecimal("available_refund_amount"));
            row.setSettlementStatus(rs.getString("settlement_status"));
            row.setReconciliationStatus(rs.getString("reconciliation_status"));
            row.setAccountingStatus(rs.getString("accounting_status"));
            row.setChannelMatchStatus(rs.getString("channel_match_status"));
            row.setChannelCode(rs.getString("channel_code"));
            row.setChannelOrderNo(rs.getString("channel_order_no"));
            row.setTransactionDateTime(localDateTime(rs, "transaction_date_time"));
            row.setRootTransactionDateTime(row.getTransactionDateTime());
            row.setTransactionTimeZone(rs.getString("transaction_time_zone"));
            return row;
        };
    }

    /**
     * 整理动作映射器，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RowMapper<TransactionOperationResponse> operationMapper() {
        return (rs, rowNum) -> {
            TransactionOperationResponse row = new TransactionOperationResponse();
            row.setOperationId(rs.getString("operation_id"));
            row.setTransactionId(rs.getString("transaction_id"));
            row.setSourceTransactionId(rs.getString("source_transaction_id"));
            row.setMerchantId(rs.getString("merchant_id"));
            row.setMerchantOrderNo(rs.getString("merchant_order_no"));
            row.setMerchantOrderId(rs.getString("merchant_order_id"));
            row.setOperationSequence(nullableInt(rs, "operation_sequence"));
            row.setTransactionType(rs.getString("transaction_type"));
            row.setTransactionStatus(rs.getString("transaction_status"));
            row.setProcessStage(rs.getString("process_stage"));
            row.setLabelCurrency(rs.getString("label_currency"));
            row.setLabelAmount(rs.getBigDecimal("label_amount"));
            row.setTransactionCurrency(rs.getString("transaction_currency"));
            row.setTransactionAmount(rs.getBigDecimal("transaction_amount"));
            row.setCurrencyExponent(nullableInt(rs, "currency_exponent"));
            row.setTransactionRate(defaultRate(rs.getBigDecimal("transaction_rate")));
            row.setDccEnabled(nullableInt(rs, "dcc_enabled"));
            row.setEdcEnabled(nullableInt(rs, "edc_enabled"));
            row.setMerchantResponseCode(resolveMerchantResponseCode(row.getTransactionStatus()));
            row.setMerchantResponseMessage(resolveMerchantResponseMessage(row.getTransactionStatus()));
            row.setChannelCode(rs.getString("channel_code"));
            row.setChannelOrderNo(rs.getString("channel_order_no"));
            row.setChannelTransactionId(rs.getString("channel_transaction_id"));
            row.setAuthCode(rs.getString("auth_code"));
            row.setAcquirerReferenceNo(rs.getString("acquirer_reference_no"));
            row.setSettlementStatus(rs.getString("settlement_status"));
            row.setReconciliationStatus(rs.getString("reconciliation_status"));
            row.setAccountingStatus(rs.getString("accounting_status"));
            row.setChannelMatchStatus(rs.getString("channel_match_status"));
            row.setTransactionDateTime(localDateTime(rs, "transaction_date_time"));
            row.setOperationTime(localDateTime(rs, "operation_time"));
            return row;
        };
    }

    /**
     * 统计汇总行映射器，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RowMapper<SummaryRow> summaryRowMapper() {
        return (rs, rowNum) -> new SummaryRow(
                rs.getString("transaction_status"),
                null,
                null,
                rs.getString("currency"),
                nullableInt(rs, "currency_exponent"),
                rs.getLong("count"),
                rs.getBigDecimal("amount"));
    }

    /**
     * 整理支付汇总行映射器，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RowMapper<SummaryRow> paymentSummaryRowMapper() {
        return (rs, rowNum) -> new SummaryRow(
                null,
                rs.getString("payment_method"),
                rs.getString("payment_brand"),
                rs.getString("currency"),
                nullableInt(rs, "currency_exponent"),
                rs.getLong("count"),
                rs.getBigDecimal("amount"));
    }

    /**
     * 整理支付工具信息映射器，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RowMapper<PaymentInfoRow> paymentInfoMapper() {
        return (rs, rowNum) -> {
            String masked = rs.getString("card_number_masked");
            String cardBin = normalizeCardBin(rs.getString("card_bin"), masked);
            return new PaymentInfoRow(
                    rs.getString("transaction_id"),
                    rs.getString("operation_id"),
                    rs.getString("payment_method"),
                    rs.getString("payment_brand"),
                    cardBin,
                    normalizeCardNumberMasked(cardBin, rs.getString("card_last4"), masked));
        };
    }

    /**
     * 整理动作可见信息映射器，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RowMapper<OperationVisibleInfoRow> operationVisibleInfoMapper() {
        return (rs, rowNum) -> {
            String masked = rs.getString("card_number_masked");
            String cardBin = normalizeCardBin(rs.getString("card_bin"), masked);
            return new OperationVisibleInfoRow(
                    rs.getString("operation_id"),
                    rs.getString("auth_code"),
                    cardBin,
                    normalizeCardNumberMasked(cardBin, rs.getString("card_last4"), masked));
        };
    }

    /**
     * 整理本地日期时间，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rs rs 输入值，参与 rs 的查询、校验、转换、写入或日志摘要
     * @param column column 输入值，参与 column 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    /**
     * 整理可空整数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rs rs 输入值，参与 rs 的查询、校验、转换、写入或日志摘要
     * @param column column 输入值，参与 column 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * 解析resolve当前金额，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param rs rs 输入值，参与 rs 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    static BigDecimal resolveCurrentAmount(String transactionType,
                                           BigDecimal transactionAmount,
                                           BigDecimal authorizedAmount) {
        if (isAuthorizationLike(transactionType)
                && authorizedAmount != null
                && authorizedAmount.signum() > 0) {
            return authorizedAmount;
        }
        return transactionAmount;
    }

    /**
     * 解析resolvelifecycle状态，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param rs rs 输入值，参与 rs 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveLifecycleStatus(TransactionOrderResponse row, ResultSet rs) throws SQLException {
        if (!"SUCCESS".equals(row.getTransactionStatus())) {
            return row.getTransactionStatus();
        }
        BigDecimal authorized = zeroIfNull(rs.getBigDecimal("authorized_amount"));
        BigDecimal captured = zeroIfNull(rs.getBigDecimal("captured_amount"));
        BigDecimal refunded = zeroIfNull(rs.getBigDecimal("refunded_amount"));
        BigDecimal availableCapture = zeroIfNull(rs.getBigDecimal("available_capture_amount"));
        BigDecimal availableRefund = zeroIfNull(rs.getBigDecimal("available_refund_amount"));
        if (isAuthorizationLike(row.getTransactionType()) && captured.signum() == 0 && refunded.signum() == 0
                && authorized.signum() > 0 && availableCapture.signum() == 0) {
            return "VOIDED";
        }
        if (refunded.signum() > 0 && availableRefund.signum() == 0) {
            return "FULLY_REFUNDED";
        }
        if (refunded.signum() > 0) {
            return "PARTIALLY_REFUNDED";
        }
        if (captured.signum() > 0 && availableCapture.signum() == 0) {
            return "CAPTURED";
        }
        if (captured.signum() > 0) {
            return "PARTIALLY_CAPTURED";
        }
        return row.getTransactionStatus();
    }

    /**
     * 判断 is authorization like 条件是否成立，用于控制 Jdbc Merchant Transaction Query Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param transactionType transaction Type 输入值，参与 交易type 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private static boolean isAuthorizationLike(String transactionType) {
        return "AUTHORIZATION".equals(transactionType) || "PRE_AUTHORIZATION".equals(transactionType) || "PRE_AUTH_COMPLETION".equals(transactionType);
    }

    /**
     * 规范化zeroifnull，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 整理默认汇率，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal defaultRate(BigDecimal value) {
        return value == null ? new BigDecimal("1.00000000") : value;
    }

    /**
     * 解析resolve商户响应编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param transactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
     */
    private String resolveMerchantResponseCode(String transactionStatus) {
        if ("SUCCESS".equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getCode();
        }
        if ("FAILED".equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_REJECTED.getCode();
        }
        if ("PENDING".equals(transactionStatus)) {
            return ApiResultEnum.PENDING.getCode();
        }
        return ApiResultEnum.PROCESSING.getCode();
    }

    /**
     * 解析resolve商户响应说明，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param transactionStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
     */
    private String resolveMerchantResponseMessage(String transactionStatus) {
        return resolveMerchantResponseMessage(transactionStatus, null);
    }

    static String resolveMerchantResponseMessage(String transactionStatus, String persistedMessage) {
        if ("FAILED".equals(transactionStatus) && StringUtils.hasText(persistedMessage)) {
            return persistedMessage.trim();
        }
        if ("SUCCESS".equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getMessage();
        }
        if ("FAILED".equals(transactionStatus)) {
            return ApiResultEnum.PAYMENT_REJECTED.getMessage();
        }
        if ("PENDING".equals(transactionStatus)) {
            return ApiResultEnum.PENDING.getMessage();
        }
        return ApiResultEnum.PROCESSING.getMessage();
    }

    /**
     * 解析resolve状态按商户响应编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param merchantResponseCode merchant Response Code 输入值，参与 商户响应码 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveStatusByMerchantResponseCode(String merchantResponseCode) {
        String code = merchantResponseCode == null ? "" : merchantResponseCode.trim();
        if (ApiResultEnum.PAYMENT_SUCCESS.getCode().equalsIgnoreCase(code) || ApiResultEnum.SUCCESS.getCode().equalsIgnoreCase(code)) {
            return "SUCCESS";
        }
        if (ApiResultEnum.PAYMENT_REJECTED.getCode().equalsIgnoreCase(code)
                || ApiResultEnum.PAYMENT_REJECTED_BY_ISSUER.getCode().equalsIgnoreCase(code)) {
            return "FAILED";
        }
        if (ApiResultEnum.PENDING.getCode().equalsIgnoreCase(code)) {
            return "PENDING";
        }
        if (ApiResultEnum.PROCESSING.getCode().equalsIgnoreCase(code)) {
            return "PROCESSING";
        }
        return null;
    }

    /**
     * 解析normalizecardnumber脱敏，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param cardBin 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param cardLast4 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param masked masked 输入值，参与 脱敏 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeCardNumberMasked(String cardBin, String cardLast4, String masked) {
        if (StringUtils.hasText(cardBin) && cardBin.length() >= 6 && StringUtils.hasText(cardLast4)) {
            return cardBin.substring(0, 6) + "****" + cardLast4;
        }
        if (!StringUtils.hasText(masked)) {
            return null;
        }
        String digits = masked.replaceAll("\\D", "");
        if (digits.length() >= 10) {
            return digits.substring(0, 6) + "****" + digits.substring(digits.length() - 4);
        }
        return masked;
    }

    /**
     * 解析normalizecardBIN，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param cardBin 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param masked masked 输入值，参与 脱敏 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeCardBin(String cardBin, String masked) {
        if (StringUtils.hasText(cardBin)) {
            String digits = cardBin.replaceAll("\\D", "");
            if (digits.length() >= 6) {
                return digits;
            }
        }
        if (!StringUtils.hasText(masked)) {
            return null;
        }
        String digits = masked.replaceAll("\\D", "");
        return digits.length() >= 6 ? digits.substring(0, 6) : null;
    }

    private record PaymentInfoRow(String transactionId, String operationId, String paymentMethod, String paymentBrand,
                                  String cardBin, String cardNumberMasked) {
    }

    private record OperationVisibleInfoRow(String operationId, String authCode, String cardBin, String cardNumberMasked) {
    }

    /** 将最早已登记季度转换为 ShardingSphere 可路由的半开范围起点。 */
    private static LocalDateTime resolveRegisteredNodeBegin(List<String> physicalNodes) {
        return physicalNodes == null ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : physicalNodes.stream()
                .filter(node -> node != null && node.matches("\\d{4}0[1-4]"))
                .min(String::compareTo)
                .map(node -> LocalDateTime.of(
                        Integer.parseInt(node.substring(0, 4)),
                        (Character.digit(node.charAt(5), 10) - 1) * 3 + 1,
                        1, 0, 0))
                .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    /** 商户归属和真实分片时间共同限定富化查询范围。 */
    private record MerchantTimeScope(String merchantId, LocalDateTime transactionDateTime) {
    }

    private record SummaryRow(String transactionStatus, String paymentMethod, String paymentBrand, String currency,
                              Integer currencyExponent, long count, BigDecimal amount) {
    }

    private static class SummaryAccumulator {
        /**
         * total Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private long totalCount;
        /**
         * success Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private long successCount;
        /**
         * failed Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private long failedCount;
        /**
         * amount Summaries，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> amountSummaries = new LinkedHashMap<>();
        /**
         * success Amount Summaries，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> successAmountSummaries = new LinkedHashMap<>();
        /**
         * failed Amount Summaries，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private final Map<String, TransactionAmountSummaryResponse> failedAmountSummaries = new LinkedHashMap<>();
        /**
         * payment Method Summaries，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final Map<String, TransactionPaymentMethodSummaryResponse> paymentMethodSummaries = new LinkedHashMap<>();

        void addAmount(SummaryRow row) {
            totalCount += row.count();
            if ("SUCCESS".equals(row.transactionStatus())) {
                successCount += row.count();
                mergeAmount(successAmountSummaries, row);
            } else if ("FAILED".equals(row.transactionStatus())) {
                failedCount += row.count();
                mergeAmount(failedAmountSummaries, row);
            }
            mergeAmount(amountSummaries, row);
        }

        void addPaymentMethod(SummaryRow row) {
            String key = (row.paymentMethod() == null ? "UNKNOWN" : row.paymentMethod()) + "|" + (row.paymentBrand() == null ? "" : row.paymentBrand());
            TransactionPaymentMethodSummaryResponse summary = paymentMethodSummaries.computeIfAbsent(key, ignored -> {
                TransactionPaymentMethodSummaryResponse value = new TransactionPaymentMethodSummaryResponse();
                value.setPaymentMethod(row.paymentMethod());
                value.setPaymentBrand(row.paymentBrand());
                value.setAmountSummaries(new ArrayList<>());
                return value;
            });
            summary.setCount(summary.getCount() + row.count());
            Map<String, TransactionAmountSummaryResponse> amounts = new LinkedHashMap<>();
            summary.getAmountSummaries().forEach(item -> amounts.put(item.getCurrency(), item));
            mergeAmount(amounts, row);
            summary.setAmountSummaries(new ArrayList<>(amounts.values()));
        }

        TransactionOperationSummaryResponse toResponse() {
            TransactionOperationSummaryResponse response = new TransactionOperationSummaryResponse();
            response.setTotalCount(totalCount);
            response.setSuccessCount(successCount);
            response.setFailedCount(failedCount);
            response.setAmountSummaries(new ArrayList<>(amountSummaries.values()));
            response.setSuccessAmountSummaries(new ArrayList<>(successAmountSummaries.values()));
            response.setFailedAmountSummaries(new ArrayList<>(failedAmountSummaries.values()));
            response.setPaymentMethodSummaries(new ArrayList<>(paymentMethodSummaries.values()));
            return response;
        }

        /**
         * 构造金额对象，完成字段复制、格式标准化和敏感数据处理。
         * <p>
         * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
         * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
         * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
         * </p>
         * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
         * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
         * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
         */
        private void mergeAmount(Map<String, TransactionAmountSummaryResponse> target, SummaryRow row) {
            TransactionAmountSummaryResponse amount = target.computeIfAbsent(row.currency(), ignored -> {
                TransactionAmountSummaryResponse value = new TransactionAmountSummaryResponse();
                value.setCurrency(row.currency());
                value.setCurrencyExponent(row.currencyExponent());
                value.setAmount(BigDecimal.ZERO);
                return value;
            });
            amount.setAmount(amount.getAmount().add(row.amount() == null ? BigDecimal.ZERO : row.amount()));
        }
    }
}
