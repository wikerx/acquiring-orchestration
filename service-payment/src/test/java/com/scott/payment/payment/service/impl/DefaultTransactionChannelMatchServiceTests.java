package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.TransactionChannelMatchResultTransactionService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionChannelMatchServiceTests
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 主动渠道查询勾兑测试，验证 BUG-VERIFY-001-001 05B 的渠道真实身份选择、恢复和 CAS 保护。
 * @status : create
 */
class DefaultTransactionChannelMatchServiceTests {

    private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 7, 23, 12, 0);

    @Test
    void shouldQueryWithRealChannelTransactionIdInsteadOfPlatformTransactionId() {
        InMemoryRecordService recordService = new InMemoryRecordService(pendingOperation());
        QueryCaptureInvokeService invokeService = new QueryCaptureInvokeService(ChannelTradeStatus.SUCCESS);
        CapturingMatchResultTransactionService resultTransactionService = new CapturingMatchResultTransactionService(recordService);

        TransactionChannelMatchResultDTO resultDTO = matchService(recordService, invokeService, resultTransactionService).matchDue(matchCommand());

        assertThat(resultDTO.getMatchedCount()).isEqualTo(1);
        assertThat(invokeService.queryInvokeCount()).isEqualTo(1);
        assertThat(invokeService.paymentInvokeCount()).isZero();
        assertThat(invokeService.lastRequest.getTransactionId()).isEqualTo("TX-PLATFORM-001");
        assertThat(invokeService.lastRequest.getChannelOrderNo()).isEqualTo("ORDER-MPGS-001");
        assertThat(invokeService.lastRequest.getChannelTransactionId()).isEqualTo("CH-MPGS-001");
        assertThat(invokeService.lastRequest.getChannelTransactionId()).isNotEqualTo("TX-PLATFORM-001");
        assertThat(recordService.completedStatus).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
    }

    @Test
    void shouldRestoreMissingChannelTransactionIdFromOriginalChannelRequest() {
        TransactionOperationDO operationDO = pendingOperation();
        operationDO.setChannelTransactionId(null);
        TransactionChannelRequestDO originalRequest = originalRequest(operationDO);
        originalRequest.setChannelTransactionId("CH-MPGS-FROM-REQUEST");
        InMemoryRecordService recordService = new InMemoryRecordService(operationDO, originalRequest);
        QueryCaptureInvokeService invokeService = new QueryCaptureInvokeService(ChannelTradeStatus.SUCCESS);
        CapturingMatchResultTransactionService resultTransactionService = new CapturingMatchResultTransactionService(recordService);

        TransactionChannelMatchResultDTO resultDTO = matchService(recordService, invokeService, resultTransactionService).matchDue(matchCommand());

        assertThat(resultDTO.getMatchedCount()).isEqualTo(1);
        assertThat(invokeService.lastRequest.getChannelOrderNo()).isEqualTo("ORDER-MPGS-001");
        assertThat(invokeService.lastRequest.getChannelTransactionId()).isEqualTo("CH-MPGS-FROM-REQUEST");
        assertThat(recordService.originalRequestUpdatedCount).isEqualTo(1);
        assertThat(recordService.lastUpdatedOriginalRequest.getRequestId()).isEqualTo("CR-ORIGINAL-001");
    }

    @Test
    void shouldNotUseLocalRequestIdAsChannelIdentityWhenMpgsTransactionIdIsMissing() {
        TransactionOperationDO operationDO = pendingOperation();
        operationDO.setChannelOrderNo(null);
        operationDO.setChannelTransactionId(null);
        operationDO.setLastChannelMatchRequestId("CR-LOCAL-ONLY");
        TransactionChannelRequestDO originalRequest = originalRequest(operationDO);
        originalRequest.setChannelOrderNo(null);
        originalRequest.setChannelTransactionId(null);
        InMemoryRecordService recordService = new InMemoryRecordService(operationDO, originalRequest);
        QueryCaptureInvokeService invokeService = new QueryCaptureInvokeService(ChannelTradeStatus.SUCCESS);
        CapturingMatchResultTransactionService resultTransactionService = new CapturingMatchResultTransactionService(recordService);

        TransactionChannelMatchResultDTO resultDTO = matchService(recordService, invokeService, resultTransactionService).matchDue(matchCommand());

        assertThat(resultDTO.getFailedCount()).isEqualTo(1);
        assertThat(invokeService.queryInvokeCount()).isZero();
        assertThat(invokeService.paymentInvokeCount()).isZero();
        assertThat(resultTransactionService.pendingCount).isEqualTo(1);
        assertThat(recordService.lastMatchResult).isEqualTo("QUERY_IDENTITY_MISSING");
        assertThat(recordService.lastFailReason).contains("transactionId=TX-PLATFORM-001")
                .contains("requestId=CR-ORIGINAL-001")
                .contains("channelCode=MPGS")
                .contains("channelMidConfigId=1001");
    }

    @Test
    void shouldNotCallChannelWhenNoSupportedIdentityExists() {
        TransactionOperationDO operationDO = pendingOperation();
        operationDO.setChannelOrderNo(null);
        operationDO.setChannelTransactionId(null);
        InMemoryRecordService recordService = new InMemoryRecordService(operationDO, null);
        QueryCaptureInvokeService invokeService = new QueryCaptureInvokeService(ChannelTradeStatus.SUCCESS);
        CapturingMatchResultTransactionService resultTransactionService = new CapturingMatchResultTransactionService(recordService);

        TransactionChannelMatchResultDTO resultDTO = matchService(recordService, invokeService, resultTransactionService).matchDue(matchCommand());

        assertThat(resultDTO.getFailedCount()).isEqualTo(1);
        assertThat(invokeService.queryInvokeCount()).isZero();
        assertThat(invokeService.paymentInvokeCount()).isZero();
        assertThat(recordService.lastMatchResult).isEqualTo("QUERY_IDENTITY_MISSING");
        assertThat(recordService.lastNextMatchTime).isAfter(TRANSACTION_TIME);
    }

    @Test
    void shouldRecoverExplicitFailureButNotOverwriteSuccessTerminal() {
        InMemoryRecordService recordService = new InMemoryRecordService(pendingOperation());
        recordService.completeShouldSucceed = false;
        QueryCaptureInvokeService invokeService = new QueryCaptureInvokeService(ChannelTradeStatus.FAILED);
        CapturingMatchResultTransactionService resultTransactionService = new CapturingMatchResultTransactionService(recordService);

        TransactionChannelMatchResultDTO resultDTO = matchService(recordService, invokeService, resultTransactionService).matchDue(matchCommand());

        assertThat(resultDTO.getPendingCount()).isEqualTo(1);
        assertThat(recordService.completeAttemptCount).isEqualTo(1);
        assertThat(recordService.completedStatus).isNull();
        assertThat(recordService.originalRequestUpdatedCount).isEqualTo(1);
        assertThat(invokeService.queryInvokeCount()).isEqualTo(1);
    }

    @Test
    void shouldKeepRecoverableStateWhenQueryIsStillUnknown() {
        InMemoryRecordService recordService = new InMemoryRecordService(pendingOperation());
        QueryCaptureInvokeService invokeService = new QueryCaptureInvokeService(ChannelTradeStatus.PROCESSING);
        CapturingMatchResultTransactionService resultTransactionService = new CapturingMatchResultTransactionService(recordService);

        TransactionChannelMatchResultDTO resultDTO = matchService(recordService, invokeService, resultTransactionService).matchDue(matchCommand());

        assertThat(resultDTO.getPendingCount()).isEqualTo(1);
        assertThat(recordService.completeAttemptCount).isZero();
        assertThat(recordService.lastMatchResult).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(recordService.lastNextMatchTime).isNotNull();
        assertThat(invokeService.queryInvokeCount()).isEqualTo(1);
    }

    @Test
    void shouldReduceQueryFrequencyForLongRunningUnknownTransactions() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        TransactionOperationDO operationDO = pendingOperation();

        operationDO.setChannelMatchCount(12);
        assertThat(DefaultTransactionChannelMatchService.nextMatchTime(operationDO, now))
                .isEqualTo(now.plusHours(6));

        operationDO.setChannelMatchCount(48);
        assertThat(DefaultTransactionChannelMatchService.nextMatchTime(operationDO, now))
                .isEqualTo(now.plusHours(24));
    }

    @Test
    void shouldNotDuplicateTerminalProgressForRepeatedActiveQuery() {
        InMemoryRecordService recordService = new InMemoryRecordService(pendingOperation());
        QueryCaptureInvokeService invokeService = new QueryCaptureInvokeService(ChannelTradeStatus.SUCCESS);
        CapturingMatchResultTransactionService resultTransactionService = new CapturingMatchResultTransactionService(recordService);
        DefaultTransactionChannelMatchService matchService = matchService(recordService, invokeService, resultTransactionService);

        TransactionChannelMatchResultDTO first = matchService.matchDue(matchCommand());
        TransactionChannelMatchResultDTO second = matchService.matchDue(matchCommand());

        assertThat(first.getMatchedCount()).isEqualTo(1);
        assertThat(second.getPendingCount()).isEqualTo(1);
        assertThat(recordService.successfulTerminalCount).isEqualTo(1);
        assertThat(recordService.completeAttemptCount).isEqualTo(2);
        assertThat(invokeService.paymentInvokeCount()).isZero();
    }

    @Test
    void shouldLetCasKeepOnlyOneTerminalWhenCallbackAndQueryRace() {
        InMemoryRecordService recordService = new InMemoryRecordService(pendingOperation());
        recordService.operation.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        QueryCaptureInvokeService invokeService = new QueryCaptureInvokeService(ChannelTradeStatus.FAILED);
        CapturingMatchResultTransactionService resultTransactionService = new CapturingMatchResultTransactionService(recordService);

        TransactionChannelMatchResultDTO resultDTO = matchService(recordService, invokeService, resultTransactionService).matchDue(matchCommand());

        assertThat(resultDTO.getPendingCount()).isEqualTo(1);
        assertThat(recordService.completedStatus).isNull();
        assertThat(recordService.operation.getTransactionStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(invokeService.paymentInvokeCount()).isZero();
    }

    @Test
    void shouldUseIndependentTransactionForActiveQueryResultPersistence() throws NoSuchMethodException {
        Method completeMethod = DefaultTransactionChannelMatchResultTransactionService.class.getMethod(
                "completeByQuery",
                TransactionOperationDO.class,
                TransactionChannelRequestDO.class,
                PaymentChannelInvokeResultDTO.class,
                ChannelTransactionStatusResolution.class,
                LocalDateTime.class);
        Method pendingMethod = DefaultTransactionChannelMatchResultTransactionService.class.getMethod(
                "markPendingByQuery",
                TransactionOperationDO.class,
                TransactionChannelRequestDO.class,
                PaymentChannelInvokeResultDTO.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class,
                String.class);

        assertRequiresNew(completeMethod);
        assertRequiresNew(pendingMethod);
    }

    private void assertRequiresNew(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    private DefaultTransactionChannelMatchService matchService(InMemoryRecordService recordService,
                                                               QueryCaptureInvokeService invokeService,
                                                               TransactionChannelMatchResultTransactionService resultTransactionService) {
        return new DefaultTransactionChannelMatchService(
                recordService,
                invokeService,
                resultTransactionService,
                restoreRouteService(),
                new DefaultChannelTransactionStatusResolver());
    }

    private TransactionChannelMatchCommandDTO matchCommand() {
        TransactionChannelMatchCommandDTO commandDTO = new TransactionChannelMatchCommandDTO();
        commandDTO.setTransactionDateTime(TRANSACTION_TIME);
        commandDTO.setChannelCode("MPGS");
        commandDTO.setLimit(10);
        return commandDTO;
    }

    private PaymentChannelRouteService restoreRouteService() {
        return new PaymentChannelRouteService() {
            @Override
            public PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO) {
                return PaymentRouteResultDTO.routed("MPGS");
            }

            @Override
            public PaymentRouteResultDTO restore(String channelCode, Long channelId, Long midConfigId, String fallbackMidNo) {
                PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed(channelCode);
                routeResultDTO.setChannelId(channelId);
                routeResultDTO.setMidConfigId(midConfigId);
                routeResultDTO.setMidNo(fallbackMidNo);
                return routeResultDTO;
            }
        };
    }

    private TransactionOperationDO pendingOperation() {
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setId(1L);
        operationDO.setOperationId("OP-001");
        operationDO.setTransactionId("TX-PLATFORM-001");
        operationDO.setSourceTransactionId("SRC-PLATFORM-001");
        operationDO.setMerchantId("200001");
        operationDO.setMerchantOrderNo("M-ORDER-001");
        operationDO.setMerchantOperationNo("M-ACTION-001");
        operationDO.setTransactionType(PaymentTransactionTypeEnum.PAYMENT.getCode());
        operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        operationDO.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        operationDO.setTransactionAmount(new BigDecimal("12.34"));
        operationDO.setTransactionCurrency("USD");
        operationDO.setChannelId(101L);
        operationDO.setChannelCode("MPGS");
        operationDO.setChannelMidConfigId(1001L);
        operationDO.setChannelTerminalId("MID-001");
        operationDO.setChannelOrderNo("ORDER-MPGS-001");
        operationDO.setChannelTransactionId("CH-MPGS-001");
        operationDO.setChannelMatchCount(0);
        operationDO.setLastChannelMatchRequestId("CR-ORIGINAL-001");
        operationDO.setTransactionDateTime(TRANSACTION_TIME);
        operationDO.setVersion(0);
        return operationDO;
    }

    private TransactionChannelRequestDO originalRequest(TransactionOperationDO operationDO) {
        TransactionChannelRequestDO requestDO = new TransactionChannelRequestDO();
        requestDO.setRequestId("CR-ORIGINAL-001");
        requestDO.setTransactionId(operationDO.getTransactionId());
        requestDO.setOperationId(operationDO.getOperationId());
        requestDO.setChannelCode(operationDO.getChannelCode());
        requestDO.setChannelOrderNo(operationDO.getChannelOrderNo());
        requestDO.setChannelTransactionId(operationDO.getChannelTransactionId());
        requestDO.setRequestStatus("TIMEOUT");
        requestDO.setPlatformResultCode(PaymentTransactionStatusEnum.PROCESSING.getCode());
        requestDO.setChannelMatchFlag(0);
        requestDO.setTransactionDateTime(operationDO.getTransactionDateTime());
        requestDO.setVersion(0);
        return requestDO;
    }

    private static class QueryCaptureInvokeService implements PaymentChannelInvokeService {

        /**
         * query Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private final ChannelTradeStatus queryStatus;

        /**
         * query Invoke Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final AtomicInteger queryInvokeCount = new AtomicInteger();

        /**
         * payment Invoke Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final AtomicInteger paymentInvokeCount = new AtomicInteger();

        /**
         * last Request，用于保存 Query Capture Invoke Service 中与 lastrequest 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private ChannelPaymentRequest lastRequest;

        private QueryCaptureInvokeService(ChannelTradeStatus queryStatus) {
            this.queryStatus = queryStatus;
        }

        /**
         * 禁止主动查询退化为普通支付调用；若该重载被调用则立即使测试失败。
         */
        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    String channelOrderNo) {
            paymentInvokeCount.incrementAndGet();
            throw new AssertionError("active query must use prepared channel identity");
        }

        /**
         * 捕获已持久化的渠道标识，并按预设查询状态构造确定性的渠道响应。
         */
        @Override
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    PaymentPreparedChannelRequestDTO preparedChannelRequest) {
            queryInvokeCount.incrementAndGet();
            ChannelPaymentRequest request = new ChannelPaymentRequest();
            request.setChannelCode(routeResult.getChannelCode());
            request.setOperationId(operationId);
            request.setTransactionId(transactionId);
            request.setChannelOrderNo(preparedChannelRequest.getChannelOrderNo());
            request.setChannelTransactionId(preparedChannelRequest.getChannelTransactionId());
            request.setTransactionType(commandDTO.getTransactionType());
            request.setAmount(commandDTO.getTransactionAmount());
            request.setCurrency(commandDTO.getTransactionCurrency());
            lastRequest = request;
            ChannelPaymentResponse response = new ChannelPaymentResponse();
            response.setChannelCode(routeResult.getChannelCode());
            response.setChannelOrderNo(preparedChannelRequest.getChannelOrderNo());
            response.setChannelTransactionId(preparedChannelRequest.getChannelTransactionId());
            response.setChannelTradeStatus(queryStatus.getCode());
            response.setRawChannelStatus(queryStatus.getCode());
            response.setChannelResponseCode(queryStatus == ChannelTradeStatus.SUCCESS ? "00" : "05");
            response.setChannelResponseMessage(queryStatus == ChannelTradeStatus.SUCCESS ? "Approved by query" : "Query not successful");
            PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
            resultDTO.setRequestId(preparedChannelRequest.getRequestId());
            resultDTO.setRequestStatus("SUCCESS");
            resultDTO.setChannelRequest(request);
            resultDTO.setChannelResponse(response);
            resultDTO.setRequestStartTime(LocalDateTime.now());
            resultDTO.setResponseTime(LocalDateTime.now());
            resultDTO.setDurationMillis(10);
            resultDTO.setHttpMethod("GET");
            resultDTO.setRequestScene("RETRIEVE");
            return resultDTO;
        }

        /**
         * 仅当原渠道订单号和渠道交易号均存在时允许主动查询。
         */
        @Override
        public boolean supportsQueryReference(PaymentCreateCommandDTO commandDTO,
                                              PaymentRouteResultDTO routeResult,
                                              String operationId,
                                              String transactionId,
                                              PaymentPreparedChannelRequestDTO preparedChannelRequest) {
            return preparedChannelRequest != null
                    && org.springframework.util.StringUtils.hasText(preparedChannelRequest.getChannelOrderNo())
                    && org.springframework.util.StringUtils.hasText(preparedChannelRequest.getChannelTransactionId());
        }

        private int queryInvokeCount() {
            return queryInvokeCount.get();
        }

        private int paymentInvokeCount() {
            return paymentInvokeCount.get();
        }
    }

    private static class CapturingMatchResultTransactionService implements TransactionChannelMatchResultTransactionService {

        /**
         * record Service 依赖，用于 Capturing Match Result Transaction Service 调用对应的数据访问、远程调用或领域服务能力。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final TransactionRecordService recordService;

        /**
         * pending Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private int pendingCount;

        private CapturingMatchResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        /**
         * 将查询终态写入原请求并委托内存仓储执行终态 CAS，供用例观察竞态结果。
         */
        @Override
        public boolean completeByQuery(TransactionOperationDO operationDO,
                                       TransactionChannelRequestDO originalRequestDO,
                                       PaymentChannelInvokeResultDTO invokeResultDTO,
                                       ChannelTransactionStatusResolution resolution,
                                       LocalDateTime matchTime) {
            recordService.updateOriginalChannelRequestByQuery(operationDO,
                    originalRequestDO,
                    invokeResultDTO,
                    resolution.getTargetStatus(),
                    resolution.getFailReasonCode());
            return recordService.completeByChannelCallback(
                    operationDO,
                    ((InMemoryRecordService) recordService).order,
                    originalRequestDO == null ? null : originalRequestDO.getRequestId(),
                    resolution.getTargetStatus(),
                    resolution.getFailReasonCode(),
                    resolution.getFailReasonMessage(),
                    resolution.getChannelStatus(),
                    resolution.getChannelResponseCode(),
                    resolution.getChannelResponseMessage());
        }

        /**
         * 记录一次仍待匹配的查询，并保存下次匹配时间及失败原因。
         */
        @Override
        public boolean markPendingByQuery(TransactionOperationDO operationDO,
                                          TransactionChannelRequestDO originalRequestDO,
                                          PaymentChannelInvokeResultDTO invokeResultDTO,
                                          String matchResult,
                                          LocalDateTime matchTime,
                                          LocalDateTime nextMatchTime,
                                          String failReason) {
            pendingCount++;
            recordService.updateOriginalChannelRequestByQuery(operationDO,
                    originalRequestDO,
                    invokeResultDTO,
                    matchResult,
                    failReason);
            return recordService.updateChannelMatch(
                    operationDO,
                    "PENDING",
                    matchResult,
                    originalRequestDO == null ? null : originalRequestDO.getRequestId(),
                    matchTime,
                    nextMatchTime,
                    failReason);
        }
    }

    private class InMemoryRecordService implements TransactionRecordService {

        /**
         * operation，用于保存 In Memory Record Service 中与 动作 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final TransactionOperationDO operation;

        /**
         * order，用于保存 In Memory Record Service 中与 订单 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final TransactionOrderDO order = new TransactionOrderDO();

        /**
         * original Request，用于保存 In Memory Record Service 中与 originalrequest 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private TransactionChannelRequestDO originalRequest;

        /**
         * complete Attempt Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private int completeAttemptCount;

        /**
         * successful Terminal Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private int successfulTerminalCount;

        /**
         * original Request Updated Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private int originalRequestUpdatedCount;

        /**
         * complete Should Succeed，用于保存 In Memory Record Service 中与 completeshouldsucceed 相关的业务属性。
         * <p>
         * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的启停取值；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private boolean completeShouldSucceed = true;

        /**
         * completed Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String completedStatus;

        /**
         * last Match Result，用于保存 In Memory Record Service 中与 lastmatchresult 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String lastMatchResult;

        /**
         * last Fail Reason，用于保存 In Memory Record Service 中与 lastfailreason 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String lastFailReason;

        /**
         * last Next Match Time，用于保存 In Memory Record Service 中与 lastnextmatchtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime lastNextMatchTime;

        /**
         * last Updated Original Request，用于保存 In Memory Record Service 中与 lastupdatedoriginalrequest 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private TransactionChannelRequestDO lastUpdatedOriginalRequest;

        private InMemoryRecordService(TransactionOperationDO operation) {
            this(operation, originalRequest(operation));
        }

        private InMemoryRecordService(TransactionOperationDO operation, TransactionChannelRequestDO originalRequest) {
            this.operation = operation;
            this.originalRequest = originalRequest;
            order.setOperationId(operation.getOperationId());
            order.setRootTransactionId(operation.getTransactionId());
            order.setLatestTransactionId(operation.getTransactionId());
            order.setMerchantId(operation.getMerchantId());
            order.setMerchantOrderNo(operation.getMerchantOrderNo());
            order.setTransactionStatus(operation.getTransactionStatus());
            order.setTransactionAmount(operation.getTransactionAmount());
            order.setTransactionCurrency(operation.getTransactionCurrency());
            order.setCurrencyExponent(2);
            order.setTransactionDateTime(operation.getTransactionDateTime());
            order.setVersion(0);
        }

        /**
         * 当前用例不创建初始交易；保留空实现以隔离渠道匹配恢复路径。
         */
        @Override
        public void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             PaymentRiskDecisionEnum riskDecisionEnum,
                                             int currencyExponent) {
        }

        /**
         * 当前用例不补写初始渠道结果；调用该方法不会改变预置交易事实。
         */
        @Override
        public void completeInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 PaymentRiskDecisionEnum riskDecisionEnum,
                                                 int currencyExponent) {
        }

        /**
         * 返回预置主单，使匹配服务在固定交易快照上执行状态判断。
         */
        @Override
        public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        /**
         * 返回预置源主单，忽略查询参数以保持测试夹具确定性。
         */
        @Override
        public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId) {
            return order;
        }

        /**
         * 返回同一预置主单，模拟已在数据库事务内取得行锁。
         */
        @Override
        public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        /**
         * 返回预置源动作单，供渠道匹配恢复逻辑读取原交易事实。
         */
        @Override
        public TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId) {
            return operation;
        }

        /**
         * 返回唯一预置动作单，模拟商户订单维度的数据库查询结果。
         */
        @Override
        public List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId,
                                                                          String merchantOrderNo,
                                                                          String transactionId,
                                                                          LocalDateTime transactionDateTime,
                                                                          LocalDateTime rootTransactionDateTime) {
            return List.of(operation);
        }

        /**
         * 返回唯一预置初始动作单，避免测试依赖真实分表查询。
         */
        @Override
        public List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo) {
            return List.of(operation);
        }

        /**
         * 固定返回无在途请款，隔离与当前渠道匹配用例无关的额度校验。
         */
        @Override
        public List<TransactionOperationDO> findNonTerminalCaptures(String merchantId,
                                                                    String operationId,
                                                                    String sourceTransactionId,
                                                                    LocalDateTime beginTime,
                                                                    LocalDateTime endTime) {
            return List.of();
        }

        /**
         * 通过预置记录模拟渠道标识反查命中。
         */
        @Override
        public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
            return operation;
        }

        /**
         * 返回唯一预置待匹配动作单，模拟调度任务的一批扫描结果。
         */
        @Override
        public List<TransactionOperationDO> listPendingChannelMatch(LocalDateTime transactionDateTime,
                                                                    String channelCode,
                                                                    LocalDateTime now,
                                                                    int limit) {
            return List.of(operation);
        }

        /**
         * 返回预置的原渠道请求，确保主动查询复用已落库的渠道标识。
         */
        @Override
        public TransactionChannelRequestDO findOriginalChannelRequestForQuery(TransactionOperationDO operationDO) {
            return originalRequest;
        }

        /**
         * 当前恢复用例不创建后续交易；保留空实现以限制测试观察范围。
         */
        @Override
        public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
        }

        /**
         * 模拟终态 CAS：终态记录或预设冲突返回失败，仅首次成功推进时记录目标状态。
         */
        @Override
        public boolean completeByChannelCallback(TransactionOperationDO operationDO,
                                                 TransactionOrderDO orderDO,
                                                 String callbackId,
                                                 String targetTransactionStatus,
                                                 String failReasonCode,
                                                 String failReasonMessage,
                                                 String channelStatus,
                                                 String channelResponseCode,
                                                 String channelResponseMessage) {
            completeAttemptCount++;
            if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operation.getTransactionStatus())
                    || PaymentTransactionStatusEnum.FAILED.getCode().equals(operation.getTransactionStatus())) {
                return false;
            }
            if (!completeShouldSucceed) {
                operation.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
                return false;
            }
            completedStatus = targetTransactionStatus;
            operation.setTransactionStatus(targetTransactionStatus);
            successfulTerminalCount++;
            return true;
        }

/**
 * 更新渠道match，保持业务状态、配置项或展示字段与请求意图一致。
 * <p>
 * 前置条件：调用方已确认 支付核心服务 中目标记录存在且当前状态允许变更。
 * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
 * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
 * </p>
 * @param operationDO operation DO 输入值，参与 动作do 的查询、校验、转换、写入或日志摘要
 * @param matchStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param matchResult match Result 输入值，参与 match结果 的查询、校验、转换、写入或日志摘要
 * @param requestId request ID 输入值，参与 请求ID 的查询、校验、转换、写入或日志摘要
 * @param matchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param nextMatchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param failReason fail Reason 输入值，参与 failreason 的查询、校验、转换、写入或日志摘要
 * @return 写入、更新或删除后的处理结果
 */
        @Override
        public boolean updateChannelMatch(TransactionOperationDO operationDO,
                                          String matchStatus,
                                          String matchResult,
                                          String requestId,
                                          LocalDateTime matchTime,
                                          LocalDateTime nextMatchTime,
                                          String failReason) {
            if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operation.getTransactionStatus())
                    || PaymentTransactionStatusEnum.FAILED.getCode().equals(operation.getTransactionStatus())) {
                return false;
            }
            lastMatchResult = matchResult;
            lastFailReason = failReason;
            lastNextMatchTime = nextMatchTime;
            return true;
        }

        /**
         * 捕获原渠道请求的查询结果，同时保持已成功的平台结果不可被非成功结果覆盖。
         */
        @Override
        public boolean updateOriginalChannelRequestByQuery(TransactionOperationDO operationDO,
                                                           TransactionChannelRequestDO originalRequestDO,
                                                           PaymentChannelInvokeResultDTO invokeResultDTO,
                                                           String platformResultCode,
                                                           String failReason) {
            if (originalRequestDO == null) {
                return true;
            }
            originalRequestUpdatedCount++;
            lastUpdatedOriginalRequest = originalRequestDO;
            if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(originalRequestDO.getPlatformResultCode())
                    && !PaymentTransactionStatusEnum.SUCCESS.getCode().equals(platformResultCode)) {
                return true;
            }
            originalRequestDO.setPlatformResultCode(platformResultCode);
            originalRequestDO.setPlatformFailReason(failReason);
            if (invokeResultDTO != null && invokeResultDTO.getChannelResponse() != null) {
                originalRequestDO.setChannelTransactionId(invokeResultDTO.getChannelResponse().getChannelTransactionId());
                originalRequestDO.setChannelStatus(invokeResultDTO.getChannelResponse().getRawChannelStatus());
            }
            return true;
        }

        /**
         * 固定返回响应日志更新成功；当前用例不检查日志表持久化细节。
         */
        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
            return true;
        }
    }
}
