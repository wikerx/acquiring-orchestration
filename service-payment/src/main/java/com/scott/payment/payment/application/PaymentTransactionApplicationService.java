package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchRequeryCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.payment.service.TransactionCallbackService;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.PaymentTransactionService;
import com.scott.payment.payment.service.TransactionLocatorService;
import com.scott.payment.payment.service.MerchantTransactionSnapshotService;
import com.scott.payment.payment.service.MerchantTransactionResultDetailService;
import com.scott.payment.payment.service.dto.MerchantTransactionResultDetailDTO;
import com.scott.payment.payment.service.dto.MerchantTransactionSnapshotDTO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionApplicationService
 * @date : 2026-07-14 12:30
 * @email : scott_x@163.com
 * @description : 收单交易应用服务，位于 service-payment 应用编排层，负责承接内部交易动作命令并委托交易服务执行幂等、风控、路由和渠道调用。
 * @status : create
 */
@Service
public class PaymentTransactionApplicationService {

    /**
     * 收单支付交易服务。
     */
    private final PaymentTransactionService paymentTransactionService;

    /**
     * 交易渠道回调服务。
     */
    private final TransactionCallbackService transactionCallbackService;

    /**
     * 渠道交易查询勾兑服务。
     */
    private final TransactionChannelMatchService transactionChannelMatchService;

    /**
     * 交易事实记录服务，用于回写 OpenAPI 响应加密摘要等审计信息。
     */
    private final TransactionRecordService transactionRecordService;

    /** 交易固定表定位服务，用于补齐商户无需感知的分片路由字段。 */
    private final TransactionLocatorService transactionLocatorService;

    /** 商户可见交易快照服务，用于同步响应和查询统一回显首次请求资料。 */
    private final MerchantTransactionSnapshotService merchantTransactionSnapshotService;

    /** 平台生成的 3DS 与财务结果读取服务。 */
    private final MerchantTransactionResultDetailService merchantTransactionResultDetailService;

    /**
     * 创建收单交易应用服务。
     *
     * @param paymentTransactionService 收单支付交易服务
     * @param transactionCallbackService 交易渠道回调服务
     * @param transactionChannelMatchService 渠道交易查询勾兑服务
     * @param transactionRecordService 交易事实记录服务
     * @param transactionLocatorService 交易固定表定位服务
     * @param merchantTransactionSnapshotService 商户可见交易快照服务
     */
    public PaymentTransactionApplicationService(PaymentTransactionService paymentTransactionService,
                                                TransactionCallbackService transactionCallbackService,
                                                TransactionChannelMatchService transactionChannelMatchService,
                                                TransactionRecordService transactionRecordService,
                                                TransactionLocatorService transactionLocatorService,
                                                MerchantTransactionSnapshotService merchantTransactionSnapshotService,
                                                MerchantTransactionResultDetailService merchantTransactionResultDetailService) {
        this.paymentTransactionService = paymentTransactionService;
        this.transactionCallbackService = transactionCallbackService;
        this.transactionChannelMatchService = transactionChannelMatchService;
        this.transactionRecordService = transactionRecordService;
        this.transactionLocatorService = transactionLocatorService;
        this.merchantTransactionSnapshotService = merchantTransactionSnapshotService;
        this.merchantTransactionResultDetailService = merchantTransactionResultDetailService;
    }

    /**
     * 创建一步支付交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    public PaymentCreateResultDTO createPayment(PaymentCreateCommandDTO commandDTO) {
        return enrichInitialResult(commandDTO, paymentTransactionService.createPayment(commandDTO));
    }

    /**
     * 创建收单授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    public PaymentCreateResultDTO createAuthorization(PaymentCreateCommandDTO commandDTO) {
        return enrichInitialResult(commandDTO, paymentTransactionService.createAuthorization(commandDTO));
    }

    /**
     * 创建预授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    public PaymentCreateResultDTO createPreAuthorization(PaymentCreateCommandDTO commandDTO) {
        return enrichInitialResult(commandDTO, paymentTransactionService.createPreAuthorization(commandDTO));
    }

    /**
     * 创建增量授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    public PaymentCreateResultDTO createIncrementalAuthorization(PaymentCreateCommandDTO commandDTO) {
        transactionLocatorService.enrichFollowUpRoute(commandDTO);
        return enrichFollowUpResult(commandDTO, paymentTransactionService.createIncrementalAuthorization(commandDTO));
    }

    /**
     * 发起请款交易。
     *
     * @param commandDTO 请款命令
     * @return 请款结果
     */
    public PaymentCreateResultDTO capture(PaymentCreateCommandDTO commandDTO) {
        transactionLocatorService.enrichFollowUpRoute(commandDTO);
        return enrichFollowUpResult(commandDTO, paymentTransactionService.capture(commandDTO));
    }

    /**
     * 发起预授权完成交易。
     *
     * @param commandDTO 预授权完成命令
     * @return 预授权完成结果
     */
    public PaymentCreateResultDTO preAuthCompletion(PaymentCreateCommandDTO commandDTO) {
        transactionLocatorService.enrichFollowUpRoute(commandDTO);
        return enrichFollowUpResult(commandDTO, paymentTransactionService.preAuthCompletion(commandDTO));
    }

    /**
     * 发起退款交易。
     *
     * @param commandDTO 退款命令
     * @return 退款结果
     */
    public PaymentCreateResultDTO refund(PaymentCreateCommandDTO commandDTO) {
        transactionLocatorService.enrichFollowUpRoute(commandDTO);
        return enrichFollowUpResult(commandDTO, paymentTransactionService.refund(commandDTO));
    }

    /**
     * 发起撤销交易。
     *
     * @param commandDTO 撤销命令
     * @return 撤销结果
     */
    public PaymentCreateResultDTO voidPayment(PaymentCreateCommandDTO commandDTO) {
        transactionLocatorService.enrichFollowUpRoute(commandDTO);
        return enrichFollowUpResult(commandDTO, paymentTransactionService.voidPayment(commandDTO));
    }

    /**
     * 查询交易状态。
     *
     * @param commandDTO 查询命令
     * @return 查询结果
     */
    public PaymentQueryResultDTO query(PaymentCreateCommandDTO commandDTO) {
        transactionLocatorService.enrichQueryRoute(commandDTO);
        PaymentQueryResultDTO resultDTO = paymentTransactionService.query(commandDTO);
        MerchantTransactionSnapshotDTO snapshot = loadSnapshot(commandDTO,
                commandDTO.getTransactionInfo().getRootTransactionId());
        resultDTO.setSubMerchantInfo(snapshot.getSubMerchantInfo());
        resultDTO.setGoodsInfo(snapshot.getGoodsInfo());
        resultDTO.setBillingCardHolderInfo(snapshot.getBillingCardHolderInfo());
        resultDTO.setPayerInfo(snapshot.getPayerInfo());
        resultDTO.setShippingInfo(snapshot.getShippingInfo());
        enrichQueryResultDetail(commandDTO, resultDTO);
        return resultDTO;
    }

    private PaymentCreateResultDTO enrichInitialResult(PaymentCreateCommandDTO commandDTO,
                                                       PaymentCreateResultDTO resultDTO) {
        return enrichResult(resultDTO, loadSnapshot(commandDTO, resultDTO.getTransactionId()));
    }

    private PaymentCreateResultDTO enrichFollowUpResult(PaymentCreateCommandDTO commandDTO,
                                                        PaymentCreateResultDTO resultDTO) {
        return enrichResult(resultDTO, loadSnapshot(
                commandDTO, commandDTO.getTransactionInfo().getRootTransactionId()));
    }

    private MerchantTransactionSnapshotDTO loadSnapshot(PaymentCreateCommandDTO commandDTO,
                                                         String rootTransactionId) {
        return merchantTransactionSnapshotService.loadSnapshots(
                commandDTO.getMerchantId(), rootTransactionId,
                commandDTO.getTransactionInfo() == null
                        ? commandDTO.getTransactionDateTime()
                        : commandDTO.getTransactionInfo().getRootTransactionDateTime() == null
                        ? commandDTO.getTransactionDateTime()
                        : commandDTO.getTransactionInfo().getRootTransactionDateTime());
    }

    private PaymentCreateResultDTO enrichResult(PaymentCreateResultDTO resultDTO,
                                                MerchantTransactionSnapshotDTO snapshot) {
        resultDTO.setSubMerchantInfo(toResultSubMerchantInfo(snapshot.getSubMerchantInfo()));
        resultDTO.setGoodsInfo(snapshot.getGoodsInfo());
        resultDTO.setBillingCardHolderInfo(snapshot.getBillingCardHolderInfo());
        resultDTO.setPayerInfo(snapshot.getPayerInfo());
        resultDTO.setShippingInfo(snapshot.getShippingInfo());
        MerchantTransactionResultDetailDTO detail = merchantTransactionResultDetailService.load(
                resultDTO.getTransactionId(), resultDTO.getTransactionDateTime());
        applyResultDetail(resultDTO, detail);
        return resultDTO;
    }

    /** 查询按精确筛选动作或动作列表最后一笔读取平台生成详情。 */
    private void enrichQueryResultDetail(PaymentCreateCommandDTO commandDTO,
                                         PaymentQueryResultDTO resultDTO) {
        if (resultDTO == null || resultDTO.getTransactionInfo() == null
                || resultDTO.getTransactionInfo().isEmpty()) {
            return;
        }
        String requestedTransactionId = commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getTransactionId();
        PaymentQueryResultDTO.TransactionInfoDTO target = resultDTO.getTransactionInfo().stream()
                .filter(item -> requestedTransactionId != null
                        && requestedTransactionId.equals(item.getTransactionId()))
                .findFirst()
                .orElse(resultDTO.getTransactionInfo().get(resultDTO.getTransactionInfo().size() - 1));
        MerchantTransactionResultDetailDTO detail = merchantTransactionResultDetailService.load(
                target.getTransactionId(), target.getTransactionDateTime());
        resultDTO.setThreeDSInfo(toQueryThreeDsInfo(detail.getThreeDsInfo()));
        resultDTO.setSettlementRate(detail.getSettlementRate());
        if (detail.getSettlementAmount() != null) {
            resultDTO.setSettlementAmount(detail.getSettlementAmount());
            resultDTO.setSettlementCurrency(detail.getSettlementCurrency());
        }
        resultDTO.setSettlementFeeAmount(detail.getSettlementFeeAmount());
        resultDTO.setFeeItems(toQueryFeeItems(detail.getFeeItems()));
    }

    /** 将平台生成详情应用到创建或后续动作响应，不伪造缺失的结算事实。 */
    private void applyResultDetail(PaymentCreateResultDTO resultDTO,
                                   MerchantTransactionResultDetailDTO detail) {
        if (detail == null) {
            return;
        }
        resultDTO.setThreeDSInfo(toResultThreeDsInfo(detail.getThreeDsInfo()));
        resultDTO.setSettlementRate(detail.getSettlementRate());
        if (detail.getSettlementAmount() != null) {
            resultDTO.setSettlementAmount(detail.getSettlementAmount());
            resultDTO.setSettlementCurrency(detail.getSettlementCurrency());
        }
        resultDTO.setSettlementFeeAmount(detail.getSettlementFeeAmount());
        resultDTO.setFeeItems(toResultFeeItems(detail.getFeeItems()));
    }

    private PaymentCreateResultDTO.ThreeDsInfoDTO toResultThreeDsInfo(
            MerchantTransactionResultDetailDTO.ThreeDsInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateResultDTO.ThreeDsInfoDTO target = new PaymentCreateResultDTO.ThreeDsInfoDTO();
        target.setEci(source.getEci());
        target.setDsTransactionId(source.getDsTransactionId());
        target.setThreeDsVersion(source.getThreeDsVersion());
        target.setStatus(source.getStatus());
        target.setLiabilityShifted(source.getLiabilityShifted());
        return target;
    }

    private PaymentCreateResultDTO.ThreeDsInfoDTO toQueryThreeDsInfo(
            MerchantTransactionResultDetailDTO.ThreeDsInfoDTO source) {
        return toResultThreeDsInfo(source);
    }

    private java.util.List<PaymentCreateResultDTO.FeeItemDTO> toResultFeeItems(
            java.util.List<MerchantTransactionResultDetailDTO.FeeItemDTO> source) {
        if (source == null || source.isEmpty()) {
            return java.util.List.of();
        }
        return source.stream().map(item -> {
            PaymentCreateResultDTO.FeeItemDTO target = new PaymentCreateResultDTO.FeeItemDTO();
            target.setCategories(item.getCategories());
            target.setAmount(item.getAmount());
            target.setCurrency(item.getCurrency());
            target.setRate(item.getRate());
            return target;
        }).toList();
    }

    private java.util.List<PaymentCreateResultDTO.FeeItemDTO> toQueryFeeItems(
            java.util.List<MerchantTransactionResultDetailDTO.FeeItemDTO> source) {
        return toResultFeeItems(source);
    }

    /** 将冻结的内部子商户快照转换为当前动作的商户响应模型。 */
    private PaymentCreateResultDTO.SubMerchantInfoDTO toResultSubMerchantInfo(
            PaymentCreateCommandDTO.SubMerchantInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateResultDTO.SubMerchantInfoDTO target = new PaymentCreateResultDTO.SubMerchantInfoDTO();
        target.setSubId(source.getSubId());
        target.setSubName(source.getSubName());
        target.setSubCompanyName(source.getSubCompanyName());
        target.setSubCountryCode(source.getSubCountryCode());
        target.setSubState(source.getSubState());
        target.setSubCity(source.getSubCity());
        target.setSubStreet(source.getSubStreet());
        target.setSubPostal(source.getSubPostal());
        target.setSubEmail(source.getSubEmail());
        target.setSubPhone(source.getSubPhone());
        target.setSubTaxId(source.getSubTaxId());
        target.setMerchantCategory(source.getMerchantCategory());
        target.setIntesCode(source.getIntesCode());
        target.setChargeType(source.getChargeType());
        return target;
    }

    /**
     * 记录渠道回调。
     *
     * @param commandDTO 渠道回调内部命令
     * @return 渠道回调记录结果
     */
    public TransactionChannelCallbackResultDTO recordChannelCallback(TransactionChannelCallbackCommandDTO commandDTO) {
        return transactionCallbackService.recordChannelCallback(commandDTO);
    }

    /**
     * 执行渠道交易查询勾兑。
     *
     * @param commandDTO 渠道查询勾兑命令
     * @return 本次处理结果
     */
    public TransactionChannelMatchResultDTO matchDueChannelTransactions(TransactionChannelMatchCommandDTO commandDTO) {
        return transactionChannelMatchService.matchDue(commandDTO);
    }

    /**
     * 使用真实交易分片时间主动重查并勾兑单笔交易。
     *
     * @param transactionId 平台交易号
     * @param commandDTO 单笔重查命令
     * @return 本次单笔勾兑处理结果
     */
    public TransactionChannelMatchResultDTO requeryChannelMatch(
            String transactionId,
            TransactionChannelMatchRequeryCommandDTO commandDTO) {
        return transactionChannelMatchService.matchOne(
                transactionId,
                commandDTO == null ? null : commandDTO.getTransactionDateTime());
    }

    /**
     * 回写商户 OpenAPI 响应密文摘要。
     *
     * @param commandDTO 响应日志回写命令
     * @return true 表示命中并更新日志
     */
    public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
        return transactionRecordService.updateMerchantApiResponseLog(commandDTO);
    }
}
