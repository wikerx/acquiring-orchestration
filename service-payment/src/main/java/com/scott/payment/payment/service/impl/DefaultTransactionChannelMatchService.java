package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.ChannelTransactionStatusResolver;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

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
                                                ChannelTransactionStatusResolver channelStatusResolver) {
        this.transactionRecordService = transactionRecordService;
        this.paymentChannelInvokeService = paymentChannelInvokeService;
        this.matchResultTransactionService = matchResultTransactionService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.channelStatusResolver = channelStatusResolver;
    }

    /**
     * 兼容旧测试和手工构造场景的构造器。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param paymentChannelInvokeService 渠道调用服务
     * @param paymentChannelRouteService 渠道路由服务
     * @param channelStatusResolver 渠道状态解析服务
     */
    public DefaultTransactionChannelMatchService(TransactionRecordService transactionRecordService,
                                                PaymentChannelInvokeService paymentChannelInvokeService,
                                                PaymentChannelRouteService paymentChannelRouteService,
                                                ChannelTransactionStatusResolver channelStatusResolver) {
        this(transactionRecordService,
                paymentChannelInvokeService,
                new DefaultTransactionChannelMatchResultTransactionService(transactionRecordService),
                paymentChannelRouteService,
                channelStatusResolver);
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
        PaymentPreparedChannelRequestDTO preparedQueryRequest = buildQueryReference(operationDO, originalRequestDO);
        PaymentCreateCommandDTO queryCommand = toQueryCommand(operationDO);
        PaymentRouteResultDTO routeResult = restoreRouteResult(operationDO);
        if (!hasSupportedQueryIdentity(queryCommand, routeResult, operationDO, preparedQueryRequest)) {
            markPending(operationDO, originalRequestDO, now, null,
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
            if ("SUCCESS".equals(resolution.getTargetStatus()) || "FAILED".equals(resolution.getTargetStatus())) {
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
        return matchResultTransactionService.completeByQuery(
                operationDO,
                originalRequestDO,
                invokeResultDTO,
                resolution,
                now);
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
        matchResultTransactionService.markPendingByQuery(operationDO,
                originalRequestDO,
                invokeResultDTO,
                reason,
                now,
                nextMatchTime(operationDO, now),
                failReason);
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
     * 使用简易递增退避，最大间隔 60 分钟，避免渠道长时间处理中时频繁查询。
     *
     * @param operationDO 待查询动作单
     * @param now 当前处理时间
     * @return 下一次查询时间
     */
    private LocalDateTime nextMatchTime(TransactionOperationDO operationDO, LocalDateTime now) {
        int matchCount = operationDO.getChannelMatchCount() == null ? 0 : operationDO.getChannelMatchCount();
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
