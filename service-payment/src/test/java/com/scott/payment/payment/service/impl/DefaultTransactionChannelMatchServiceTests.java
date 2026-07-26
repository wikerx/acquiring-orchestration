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
         * query Status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final ChannelTradeStatus queryStatus;

        /**
         * query Invoke Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final AtomicInteger queryInvokeCount = new AtomicInteger();

        /**
         * payment Invoke Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final AtomicInteger paymentInvokeCount = new AtomicInteger();

        /**
         * last Request 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private ChannelPaymentRequest lastRequest;

        private QueryCaptureInvokeService(ChannelTradeStatus queryStatus) {
            this.queryStatus = queryStatus;
        }

        @Override
/**
 * 完成 invoke 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResult route Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param channelOrderNo channel Order No 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
        public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                    PaymentRouteResultDTO routeResult,
                                                    String operationId,
                                                    String transactionId,
                                                    String channelOrderNo) {
            paymentInvokeCount.incrementAndGet();
            throw new AssertionError("active query must use prepared channel identity");
        }

        @Override
/**
 * 完成 invoke 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResult route Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param preparedChannelRequest prepared Channel Request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
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

        @Override
/**
 * 完成 supports Query Reference 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResult route Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param preparedChannelRequest prepared Channel Request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
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
         * record Service 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionRecordService recordService;

        /**
         * pending Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int pendingCount;

        private CapturingMatchResultTransactionService(TransactionRecordService recordService) {
            this.recordService = recordService;
        }

        @Override
/**
 * 推进 complete By Query 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param originalRequestDO original Request DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resolution resolution 输入值，含义由调用方法名称和所属业务对象限定
 * @param matchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 当前方法计算或转换后的业务结果
 */
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

        @Override
/**
 * 推进 mark Pending By Query 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param originalRequestDO original Request DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param matchResult match Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param matchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param nextMatchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param failReason fail Reason 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
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
         * operation 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionOperationDO operation;

        /**
         * order 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionOrderDO order = new TransactionOrderDO();

        /**
         * original Request 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private TransactionChannelRequestDO originalRequest;

        /**
         * complete Attempt Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int completeAttemptCount;

        /**
         * successful Terminal Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int successfulTerminalCount;

        /**
         * original Request Updated Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int originalRequestUpdatedCount;

        /**
         * complete Should Succeed 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean completeShouldSucceed = true;

        /**
         * completed Status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String completedStatus;

        /**
         * last Match Result 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String lastMatchResult;

        /**
         * last Fail Reason 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String lastFailReason;

        /**
         * last Next Match Time 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private LocalDateTime lastNextMatchTime;

        /**
         * last Updated Original Request 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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

        @Override
/**
 * 写入或更新 record Initial Transaction 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelInvokeResultDTO channel Invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskDecisionEnum risk Decision Enum 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
        public void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                             PaymentRouteResultDTO routeResultDTO,
                                             PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                             PaymentCreateResultDTO resultDTO,
                                             PaymentRiskDecisionEnum riskDecisionEnum,
                                             int currencyExponent) {
        }

        @Override
/**
 * 推进 complete Initial Channel Result 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param routeResultDTO route Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelInvokeResultDTO channel Invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param resultDTO result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskDecisionEnum risk Decision Enum 输入值，含义由调用方法名称和所属业务对象限定
 * @param currencyExponent 币种代码，格式为 ISO 4217 三位大写字母
 */
        public void completeInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 PaymentRiskDecisionEnum riskDecisionEnum,
                                                 int currencyExponent) {
        }

        @Override
        /**
         * 查询 find Order 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
         * @return 解析或查询得到的业务值
         */
        public TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        @Override
        /**
         * 查询 find Source Order By Transaction Id 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 解析或查询得到的业务值
         */
        public TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId) {
            return order;
        }

        @Override
        /**
         * 完成 lock Order 分支的校验或转换，返回值供当前调用链继续组装结果。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
         * @return 当前方法计算或转换后的业务结果
         */
        public TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId) {
            return order;
        }

        @Override
        /**
         * 查询 find Source Operation By Transaction Id 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId) {
            return operation;
        }

        @Override
        /**
         * 查询 find Operations By Merchant Order 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
         * @param merchantOrderNo 商户订单号，用于商户侧幂等校验和订单查询
         * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId, String merchantOrderNo, String transactionId) {
            return List.of(operation);
        }

        @Override
        /**
         * 查询 find Initial Operations By Merchant Order 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
         * @param merchantOrderNo 商户订单号，用于商户侧幂等校验和订单查询
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo) {
            return List.of(operation);
        }

        @Override
/**
 * 查询 find Non Terminal Captures 所需数据，未命中时按调用场景返回空值或抛出异常。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
 * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
 * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @return 解析或查询得到的业务值
 */
        public List<TransactionOperationDO> findNonTerminalCaptures(String merchantId,
                                                                    String operationId,
                                                                    String sourceTransactionId,
                                                                    LocalDateTime beginTime,
                                                                    LocalDateTime endTime) {
            return List.of();
        }

        @Override
        /**
         * 查询 find Operation By Channel Transaction 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param channelOrderNo channel Order No 输入值，含义由调用方法名称和所属业务对象限定
         * @param channelTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
         * @return 渠道 API 操作类型或平台操作映射结果
         */
        public TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId) {
            return operation;
        }

        @Override
/**
 * 完成 list Pending Channel Match 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param channelCode channel Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
 * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
        public List<TransactionOperationDO> listPendingChannelMatch(LocalDateTime transactionDateTime,
                                                                    String channelCode,
                                                                    LocalDateTime now,
                                                                    int limit) {
            return List.of(operation);
        }

        @Override
        /**
         * 查询 find Original Channel Request For Query 所需数据，未命中时按调用场景返回空值或抛出异常。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
         * @return 解析或查询得到的业务值
         */
        public TransactionChannelRequestDO findOriginalChannelRequestForQuery(TransactionOperationDO operationDO) {
            return originalRequest;
        }

        @Override
        /**
         * 写入或更新 record Follow Up Transaction 相关数据，保持数据库记录与当前业务处理结果一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param recordDTO record DTO 输入值，含义由调用方法名称和所属业务对象限定
         */
        public void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO) {
        }

        @Override
/**
 * 推进 complete By Channel Callback 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param orderDO order DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param callbackId callback Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param targetTransactionStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param failReasonCode fail Reason Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param failReasonMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 当前方法计算或转换后的业务结果
 */
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

        @Override
/**
 * 写入或更新 update Channel Match 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param matchStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param matchResult match Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param requestId request Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param matchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param nextMatchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param failReason fail Reason 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
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

        @Override
/**
 * 写入或更新 update Original Channel Request By Query 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param originalRequestDO original Request DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param platformResultCode platform Result Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param failReason fail Reason 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
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

        @Override
        /**
         * 写入或更新 update Merchant Api Response Log 相关数据，保持数据库记录与当前业务处理结果一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
         * @return 当前方法计算或转换后的业务结果
         */
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
            return true;
        }
    }
}
