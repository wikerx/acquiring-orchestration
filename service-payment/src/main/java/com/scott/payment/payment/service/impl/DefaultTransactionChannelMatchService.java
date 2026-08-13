package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.config.ChannelMatchAbnormalProperties;
import com.scott.payment.payment.domain.reconciliation.ChannelMatchAbnormalTypeEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.ChannelTransactionStatusResolver;
import com.scott.payment.payment.service.ChannelMatchAbnormalService;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionChannelMatchResultTransactionService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionChannelMatchService
 * @date : 2026-07-19 22:30
 * @email : scott_x@163.com
 * @description : 默认渠道交易查询勾兑服务，位于 service-payment 服务实现层，按原动作单保存的渠道和 MID 快照发起 QUERY；仅在渠道查询确认 SUCCESS/FAILED 时推进终态，网络和解析异常只保留待下次勾兑。
 * @status : create
 */
@Service
public class DefaultTransactionChannelMatchService implements TransactionChannelMatchService {

    /**
     * DEFAULT LIMIT，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；不允许为空；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_LIMIT = 100;

    /**
     * MAX LIMIT，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；不允许为空；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int MAX_LIMIT = 500;

    /**
     * 缺少渠道查询身份时按天重试，等待迟到回调或人工补齐渠道身份，避免无效高频查询。
     */
    private static final long MISSING_IDENTITY_RETRY_HOURS = 24L;

    /** 只有真正尝试发送过的资金请求才允许进入渠道状态查询。 */
    private static final Set<String> QUERYABLE_ORIGINAL_REQUEST_STATUSES = Set.of(
            "SENT", "SUCCESS", "TIMEOUT", "FAILED");

    /**
     * transaction Record Service 依赖，用于 Default Transaction Channel Match Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * payment Channel Invoke Service 依赖，用于 Default Transaction Channel Match Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentChannelInvokeService paymentChannelInvokeService;

    /**
     * match Result Transaction Service 依赖，用于 Default Transaction Channel Match Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionChannelMatchResultTransactionService matchResultTransactionService;

    /**
     * payment Channel Route Service 依赖，用于 Default Transaction Channel Match Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentChannelRouteService paymentChannelRouteService;

    /**
     * channel Status Resolver，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private final ChannelTransactionStatusResolver channelStatusResolver;

    /** 自动异常升级配置。 */
    private final ChannelMatchAbnormalProperties abnormalProperties;

    /** 延迟获取异常服务，避免人工重查服务与自动勾兑服务形成构造器环。 */
    private final ObjectProvider<ChannelMatchAbnormalService> abnormalServiceProvider;

    /**
     * 创建默认渠道交易查询勾兑服务。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param paymentChannelInvokeService 渠道调用服务
     * @param paymentChannelRouteService 渠道路由服务
     * @param channelStatusResolver 渠道状态解析服务
     */
    @Autowired
    public DefaultTransactionChannelMatchService(TransactionRecordService transactionRecordService,
                                                PaymentChannelInvokeService paymentChannelInvokeService,
                                                TransactionChannelMatchResultTransactionService matchResultTransactionService,
                                                PaymentChannelRouteService paymentChannelRouteService,
                                                ChannelTransactionStatusResolver channelStatusResolver,
                                                ChannelMatchAbnormalProperties abnormalProperties,
                                                ObjectProvider<ChannelMatchAbnormalService> abnormalServiceProvider) {
        this.transactionRecordService = transactionRecordService;
        this.paymentChannelInvokeService = paymentChannelInvokeService;
        this.matchResultTransactionService = matchResultTransactionService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.channelStatusResolver = channelStatusResolver;
        this.abnormalProperties = abnormalProperties;
        this.abnormalServiceProvider = abnormalServiceProvider;
    }

    /**
     * 保留既有单元测试和非 Spring 构造方式；该路径不启用异常自动建案。
     */
    public DefaultTransactionChannelMatchService(TransactionRecordService transactionRecordService,
                                                 PaymentChannelInvokeService paymentChannelInvokeService,
                                                 TransactionChannelMatchResultTransactionService matchResultTransactionService,
                                                 PaymentChannelRouteService paymentChannelRouteService,
                                                 ChannelTransactionStatusResolver channelStatusResolver) {
        this.transactionRecordService = transactionRecordService;
        this.paymentChannelInvokeService = paymentChannelInvokeService;
        this.matchResultTransactionService = matchResultTransactionService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.channelStatusResolver = channelStatusResolver;
        this.abnormalProperties = new ChannelMatchAbnormalProperties();
        this.abnormalServiceProvider = null;
    }

    /**
     * 处理待渠道查询确认的交易动作。
     *
     * @param commandDTO 查询勾兑命令
     * @return 本次处理结果
     */
    @Override
    public TransactionChannelMatchResultDTO matchDue(TransactionChannelMatchCommandDTO commandDTO) {
        LocalDateTime transactionDateTime = commandDTO == null ? null : commandDTO.getTransactionDateTime();
        LocalDateTime now = LocalDateTime.now();
        int limit = normalizeLimit(commandDTO == null ? null : commandDTO.getLimit());
        TransactionChannelMatchResultDTO resultDTO = new TransactionChannelMatchResultDTO();
        for (TransactionOperationDO operationDO : transactionRecordService.listPendingChannelMatch(
                transactionDateTime,
                commandDTO == null ? null : commandDTO.getChannelCode(),
                now,
                limit)) {
            resultDTO.setScannedCount(resultDTO.getScannedCount() + 1);
            processOne(operationDO, now, resultDTO);
        }
        return resultDTO;
    }

    /**
     * 使用交易号和真实分片时间主动查询单笔非终态动作。
     *
     * @param transactionId 平台交易号
     * @param transactionDateTime 动作真实分片时间
     * @return 单笔勾兑处理结果
     */
    @Override
    public TransactionChannelMatchResultDTO matchOne(String transactionId,
                                                      LocalDateTime transactionDateTime) {
        TransactionChannelMatchResultDTO resultDTO = new TransactionChannelMatchResultDTO();
        TransactionOperationDO operationDO = transactionRecordService.findSourceOperationByTransactionId(
                transactionId, transactionDateTime);
        if (operationDO == null) {
            throw new com.scott.payment.component.core.exception.ServiceException(
                    com.scott.payment.component.core.enums.ApiResultEnum.ORDER_NOT_FOUND);
        }
        resultDTO.setScannedCount(1);
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operationDO.getTransactionStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(operationDO.getTransactionStatus())) {
            resultDTO.setMatchedCount(1);
            return resultDTO;
        }
        processOne(operationDO, LocalDateTime.now(), resultDTO);
        return resultDTO;
    }

    /**
     * 查询并处理单笔待勾兑动作。
     * <p>
     * 只有渠道查询结果被解析为 SUCCESS/FAILED 终态时才推进交易；未识别、处理中、网络异常和解析异常都保留为待勾兑，
     * 避免把渠道可能已经成功的资金动作误标失败。
     *
     * @param operationDO 待查询动作单
     * @param now 本次勾兑时间
     * @param resultDTO 本次任务统计结果
     */
    private void processOne(TransactionOperationDO operationDO,
                            LocalDateTime now,
                            TransactionChannelMatchResultDTO resultDTO) {
        TransactionChannelRequestDO originalRequestDO = transactionRecordService.findOriginalChannelRequestForQuery(operationDO);
        if (originalRequestDO != null && !isQueryableOriginalRequest(originalRequestDO)) {
            resultDTO.setPendingCount(resultDTO.getPendingCount() + 1);
            return;
        }
        PaymentPreparedChannelRequestDTO preparedQueryRequest = buildQueryReference(operationDO, originalRequestDO);
        PaymentCreateCommandDTO queryCommand = toQueryCommand(operationDO);
        PaymentRouteResultDTO routeResult = restoreRouteResult(operationDO);
        if (!hasSupportedQueryIdentity(queryCommand, routeResult, operationDO, preparedQueryRequest)) {
            markPending(operationDO, originalRequestDO, now, now.plusHours(MISSING_IDENTITY_RETRY_HOURS), null,
                    "QUERY_IDENTITY_MISSING",
                    missingIdentityReason(operationDO, originalRequestDO));
            resultDTO.setFailedCount(resultDTO.getFailedCount() + 1);
            return;
        }
        try {
            PaymentChannelInvokeResultDTO invokeResultDTO = paymentChannelInvokeService.invoke(
                    queryCommand,
                    routeResult,
                    operationDO.getOperationId(),
                    operationDO.getTransactionId(),
                    preparedQueryRequest);
            ChannelPaymentResponse response = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
            ChannelTransactionStatusResolution resolution = channelStatusResolver.resolveSync(
                    operationDO.getChannelCode(),
                    operationDO.getTransactionType(),
                    response);
            if (!resolution.resolved()) {
                markPending(operationDO, originalRequestDO, now, invokeResultDTO, "channel query status can not be mapped yet");
                resultDTO.setPendingCount(resultDTO.getPendingCount() + 1);
                return;
            }
            String mismatchType = moneyMismatchType(operationDO, response);
            if (mismatchType != null) {
                markMoneyMismatch(operationDO, originalRequestDO, now, invokeResultDTO, mismatchType);
                resultDTO.setPendingCount(resultDTO.getPendingCount() + 1);
                return;
            }
            if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resolution.getTargetStatus())
                    || PaymentTransactionStatusEnum.FAILED.getCode().equals(resolution.getTargetStatus())) {
                if (complete(operationDO, originalRequestDO, invokeResultDTO, resolution, now)) {
                    resultDTO.setMatchedCount(resultDTO.getMatchedCount() + 1);
                    return;
                }
                resultDTO.setPendingCount(resultDTO.getPendingCount() + 1);
                return;
            }
            markPending(operationDO, originalRequestDO, now, invokeResultDTO, resolution.getTargetStatus());
            resultDTO.setPendingCount(resultDTO.getPendingCount() + 1);
        } catch (RuntimeException exception) {
            // 查询异常无法证明渠道失败，资金动作必须保持非终态，等待下一次查询或回调确认。
            markPending(operationDO, originalRequestDO, now, null, "QUERY_EXCEPTION", exception.getMessage());
            resultDTO.setFailedCount(resultDTO.getFailedCount() + 1);
        }
    }

    private boolean isQueryableOriginalRequest(TransactionChannelRequestDO originalRequestDO) {
        return originalRequestDO != null
                && StringUtils.hasText(originalRequestDO.getRequestStatus())
                && QUERYABLE_ORIGINAL_REQUEST_STATUSES.contains(
                        originalRequestDO.getRequestStatus().trim().toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * 比较渠道查询响应明确返回的币种；缺少结构化金额信息时不推断异常。
     */
    private String moneyMismatchType(TransactionOperationDO operationDO, ChannelPaymentResponse response) {
        if (operationDO == null || response == null
                || !StringUtils.hasText(operationDO.getTransactionCurrency())
                || !StringUtils.hasText(response.getChannelCurrency())) {
            return null;
        }
        if (!operationDO.getTransactionCurrency().trim().equalsIgnoreCase(response.getChannelCurrency().trim())) {
            return ChannelMatchAbnormalTypeEnum.CURRENCY_MISMATCH.getCode();
        }
        if (operationDO.getTransactionAmount() != null && response.getChannelAmount() != null
                && operationDO.getTransactionAmount().compareTo(response.getChannelAmount()) != 0) {
            return ChannelMatchAbnormalTypeEnum.AMOUNT_MISMATCH.getCode();
        }
        return null;
    }

    /**
     * 金额或币种不一致属于确定性异常，首次发现即转人工复核并阻止终态推进。
     */
    private void markMoneyMismatch(TransactionOperationDO operationDO,
                                   TransactionChannelRequestDO originalRequestDO,
                                   LocalDateTime now,
                                   PaymentChannelInvokeResultDTO invokeResultDTO,
                                   String abnormalType) {
        boolean updated = matchResultTransactionService.markPendingByQuery(
                operationDO,
                originalRequestDO,
                invokeResultDTO,
                "REVIEW_REQUIRED",
                abnormalType,
                now,
                nextMatchTime(operationDO, now),
                null);
        if (!updated) {
            return;
        }
        ChannelMatchAbnormalService abnormalService = abnormalService();
        if (abnormalService != null) {
            abnormalService.recordReviewRequired(
                    operationDO,
                    abnormalType,
                    abnormalType,
                    abnormalType,
                    originalRequestDO == null ? null : originalRequestDO.getRequestId(),
                    invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse(),
                    now);
        }
    }

/**
 * 构造queryreference对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param originalRequestDO original Request DO 输入值，参与 original请求do 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
    private PaymentPreparedChannelRequestDTO buildQueryReference(TransactionOperationDO operationDO,
                                                                TransactionChannelRequestDO originalRequestDO) {
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId(originalRequestDO == null ? operationDO.getLastChannelMatchRequestId() : originalRequestDO.getRequestId());
        prepared.setChannelOrderNo(firstText(operationDO.getChannelOrderNo(),
                originalRequestDO == null ? null : originalRequestDO.getChannelOrderNo()));
        prepared.setChannelTransactionId(firstText(operationDO.getChannelTransactionId(),
                originalRequestDO == null ? null : originalRequestDO.getChannelTransactionId()));
        return prepared;
    }

/**
 * 判断 has supported query identity 条件是否成立，用于控制 Default Transaction Channel Match Service 的后续分支。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 判断所需的对象、枚举或配置。
 * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
 * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
 * </p>
 * @param queryCommand query Command 输入值，参与 querycommand 的查询、校验、转换、写入或日志摘要
 * @param routeResult route Result 输入值，参与 route结果 的查询、校验、转换、写入或日志摘要
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param preparedQueryRequest prepared Query Request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @return 条件满足时返回 true，否则返回 false
 */
    private boolean hasSupportedQueryIdentity(PaymentCreateCommandDTO queryCommand,
                                              PaymentRouteResultDTO routeResult,
                                              TransactionOperationDO operationDO,
                                              PaymentPreparedChannelRequestDTO preparedQueryRequest) {
        if (operationDO == null || preparedQueryRequest == null) {
            return false;
        }
        return paymentChannelInvokeService.supportsQueryReference(queryCommand,
                routeResult,
                operationDO.getOperationId(),
                operationDO.getTransactionId(),
                preparedQueryRequest);
    }

    /**
     * 规范化missingidentityreason，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
     * @param originalRequestDO original Request DO 输入值，参与 original请求do 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String missingIdentityReason(TransactionOperationDO operationDO, TransactionChannelRequestDO originalRequestDO) {
        String requestId = firstText(originalRequestDO == null ? null : originalRequestDO.getRequestId(),
                operationDO == null ? null : operationDO.getLastChannelMatchRequestId());
        String transactionId = operationDO == null ? null : operationDO.getTransactionId();
        String channelCode = operationDO == null ? null : operationDO.getChannelCode();
        Long channelMidConfigId = operationDO == null ? null : operationDO.getChannelMidConfigId();
        return "no supported channel query identity, transactionId=" + transactionId
                + ", requestId=" + requestId
                + ", channelCode=" + channelCode
                + ", channelMidConfigId=" + channelMidConfigId;
    }

    /**
     * 使用渠道查询确认结果推进交易终态。
     * <p>
     * 这里进入独立结果事务并复用回调终态推进服务，以保持 CAS、状态历史、金额汇总和商户通知激活口径一致。
     *
     * @param operationDO 待推进动作单
     * @param originalRequestDO 原资金动作渠道请求记录
     * @param invokeResultDTO 渠道查询调用结果
     * @param resolution 渠道查询解析出的终态结果
     * @param now 当前处理时间
     * @return true 表示终态推进成功
     */
    private boolean complete(TransactionOperationDO operationDO,
                             TransactionChannelRequestDO originalRequestDO,
                             PaymentChannelInvokeResultDTO invokeResultDTO,
                             ChannelTransactionStatusResolution resolution,
                             LocalDateTime now) {
        boolean completed = matchResultTransactionService.completeByQuery(
                operationDO,
                originalRequestDO,
                invokeResultDTO,
                resolution,
                now);
        if (completed) {
            ChannelMatchAbnormalService abnormalService = abnormalService();
            if (abnormalService != null) {
                abnormalService.autoResolve(operationDO.getTransactionId(), operationDO.getTransactionDateTime(),
                        originalRequestDO == null ? null : originalRequestDO.getRequestId(), now);
            }
        }
        return completed;
    }

    /**
     * 标记动作单继续等待渠道查询或回调确认。
     *
     * @param operationDO 待更新动作单
     * @param originalRequestDO 原资金动作渠道请求记录，可为空
     * @param now 本次勾兑时间
     * @param invokeResultDTO 本次渠道查询调用上下文，可为空
     * @param reason 勾兑结果摘要
     */
    private void markPending(TransactionOperationDO operationDO,
                             TransactionChannelRequestDO originalRequestDO,
                             LocalDateTime now,
                             PaymentChannelInvokeResultDTO invokeResultDTO,
                             String reason) {
        markPending(operationDO, originalRequestDO, now, invokeResultDTO, reason, null);
    }

    /**
     * 标记动作单继续等待并记录异常摘要。
     * <p>
     * QUERY 异常、响应无法解析、身份缺失或非终态结果都不能证明资金失败，因此只更新勾兑摘要、原请求号和下次查询时间。
     *
     * @param operationDO 待更新动作单
     * @param originalRequestDO 原资金动作渠道请求记录，可为空
     * @param now 本次勾兑时间
     * @param invokeResultDTO 本次渠道查询调用上下文，可为空
     * @param reason 勾兑结果摘要
     * @param failReason 技术异常摘要，可为空
     */
    private void markPending(TransactionOperationDO operationDO,
                             TransactionChannelRequestDO originalRequestDO,
                             LocalDateTime now,
                             PaymentChannelInvokeResultDTO invokeResultDTO,
                             String reason,
                             String failReason) {
        markPending(operationDO,
                originalRequestDO,
                now,
                nextMatchTime(operationDO, now),
                invokeResultDTO,
                reason,
                failReason);
    }

    /**
     * 保存渠道查询仍未终结的结果，并使用指定时间安排后续补偿扫描。
     *
     * @param operationDO       当前 QUERY 动作单
     * @param originalRequestDO 原渠道请求
     * @param now               本次查询时间
     * @param nextMatchTime     下次允许补匹配的时间
     * @param invokeResultDTO   渠道查询结果
     * @param reason            待处理原因编码
     * @param failReason        脱敏失败说明
     */
    private void markPending(TransactionOperationDO operationDO,
                             TransactionChannelRequestDO originalRequestDO,
                             LocalDateTime now,
                             LocalDateTime nextMatchTime,
                             PaymentChannelInvokeResultDTO invokeResultDTO,
                             String reason,
                             String failReason) {
        boolean reviewRequired = shouldEscalate(operationDO);
        String matchStatus = reviewRequired ? "REVIEW_REQUIRED" : "PENDING";
        boolean updated = matchResultTransactionService.markPendingByQuery(operationDO,
                originalRequestDO,
                invokeResultDTO,
                matchStatus,
                reason,
                now,
                nextMatchTime,
                failReason);
        if (updated && reviewRequired) {
            ChannelMatchAbnormalService abnormalService = abnormalService();
            if (abnormalService != null) {
                String abnormalType = "QUERY_IDENTITY_MISSING".equals(reason)
                        ? ChannelMatchAbnormalTypeEnum.QUERY_IDENTITY_MISSING.getCode()
                        : ChannelMatchAbnormalTypeEnum.QUERY_RESULT_UNKNOWN.getCode();
                abnormalService.recordReviewRequired(operationDO, abnormalType,
                        reason, reason,
                        originalRequestDO == null ? null : originalRequestDO.getRequestId(), now);
            }
        }
    }

    private boolean shouldEscalate(TransactionOperationDO operationDO) {
        if (!abnormalProperties.isEnabled()) {
            return false;
        }
        int threshold = Math.max(1, abnormalProperties.getReviewRequiredThreshold());
        int currentCount = operationDO == null || operationDO.getChannelMatchCount() == null
                ? 0 : operationDO.getChannelMatchCount();
        return currentCount + 1 >= threshold;
    }

    private ChannelMatchAbnormalService abnormalService() {
        return abnormalServiceProvider == null ? null : abnormalServiceProvider.getIfAvailable();
    }

    /**
     * 构造渠道 QUERY 命令。
     * <p>
     * 查询勾兑复用 payment 的渠道调用服务，但交易类型固定为 QUERY，金额和币种只作为渠道查询上下文，
     * 不会创建新的支付动作或重新计算金额。
     *
     * @param operationDO 待查询动作单
     * @return 查询命令
     */
    private PaymentCreateCommandDTO toQueryCommand(TransactionOperationDO operationDO) {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId(operationDO.getMerchantId());
        commandDTO.setMerchantOrderNo(operationDO.getMerchantOrderNo());
        commandDTO.setMerchantOrderId(firstText(operationDO.getMerchantOrderId(), operationDO.getMerchantOperationNo()));
        commandDTO.setTransactionType("QUERY");
        commandDTO.setTransactionDateTime(operationDO.getTransactionDateTime());
        commandDTO.setAmount(operationDO.getTransactionAmount());
        commandDTO.setCurrency(operationDO.getTransactionCurrency());
        commandDTO.setTransactionAmount(operationDO.getTransactionAmount());
        commandDTO.setTransactionCurrency(operationDO.getTransactionCurrency());
        return commandDTO;
    }

    /**
     * 整理首个非空文本，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param first first 输入值，参与 首个 的查询、校验、转换、写入或日志摘要
     * @param second second 输入值，参与 second 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    /**
     * 恢复原交易动作的渠道路由快照。
     * <p>
     * 勾兑查询必须使用原动作单上的 channel_id、channel_mid_config_id、channel_order_no 和 channel_transaction_id；
     * 不能重新路由，否则渠道配置变更后可能查错 MID，导致误判交易状态。
     *
     * @param operationDO 待查询动作单
     * @return 原渠道调用参数
     */
    private PaymentRouteResultDTO restoreRouteResult(TransactionOperationDO operationDO) {
        return paymentChannelRouteService.restore(
                operationDO.getChannelCode(),
                operationDO.getChannelId(),
                operationDO.getChannelMidConfigId(),
                operationDO.getChannelTerminalId());
    }

    /**
     * 计算下一次查询时间。
     * <p>
     * 前 12 次按 5 分钟递增，之后降为每 6 小时；超过 48 次后每天查询一次。
     * 未确认资金结果不能按重试次数自动判失败，因此长期交易仍保留自动恢复入口。
     *
     * @param operationDO 待查询动作单
     * @param now 当前处理时间
     * @return 下一次查询时间
     */
    static LocalDateTime nextMatchTime(TransactionOperationDO operationDO, LocalDateTime now) {
        int matchCount = operationDO.getChannelMatchCount() == null ? 0 : operationDO.getChannelMatchCount();
        if (matchCount >= 48) {
            return now.plusHours(24);
        }
        if (matchCount >= 12) {
            return now.plusHours(6);
        }
        long minutes = Math.min(60L, Math.max(1L, matchCount + 1L) * 5L);
        return now.plusMinutes(minutes);
    }

    /**
     * 规范化单次扫描数量。
     *
     * @param limit 外部传入扫描数量
     * @return 限制后的扫描数量
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
