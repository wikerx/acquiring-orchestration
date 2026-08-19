package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.entity.TransactionAuthenticationInfoDO;
import com.scott.payment.payment.entity.TransactionFinanceStateDO;
import com.scott.payment.payment.mapper.TransactionAuthenticationInfoMapper;
import com.scott.payment.payment.mapper.TransactionFinanceStateMapper;
import com.scott.payment.payment.service.MerchantTransactionResultDetailService;
import com.scott.payment.payment.service.dto.MerchantTransactionResultDetailDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantTransactionResultDetailService
 * @date : 2026-08-14 13:45
 * @email : scott_x@163.com
 * @description : 从认证和财务逻辑表读取商户可见结果，严格排除 CAVV、认证原文和未形成的结算数据。
 * @status : create
 */
@Service
public class DefaultMerchantTransactionResultDetailService implements MerchantTransactionResultDetailService {

    private final TransactionAuthenticationInfoMapper authenticationInfoMapper;
    private final TransactionFinanceStateMapper financeStateMapper;

    /** 创建商户交易结果详情服务。 */
    public DefaultMerchantTransactionResultDetailService(
            TransactionAuthenticationInfoMapper authenticationInfoMapper,
            TransactionFinanceStateMapper financeStateMapper) {
        this.authenticationInfoMapper = authenticationInfoMapper;
        this.financeStateMapper = financeStateMapper;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public MerchantTransactionResultDetailDTO load(String transactionId, LocalDateTime transactionDateTime) {
        MerchantTransactionResultDetailDTO target = new MerchantTransactionResultDetailDTO();
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null) {
            return target;
        }
        fillThreeDs(target, authenticationInfoMapper.selectLatestByTransaction(
                transactionId, transactionDateTime));
        fillFinance(target, financeStateMapper.selectByTransaction(transactionId, transactionDateTime));
        return target;
    }

    private void fillThreeDs(MerchantTransactionResultDetailDTO target,
                             TransactionAuthenticationInfoDO source) {
        if (source == null) {
            return;
        }
        MerchantTransactionResultDetailDTO.ThreeDsInfoDTO threeDs =
                new MerchantTransactionResultDetailDTO.ThreeDsInfoDTO();
        threeDs.setEci(source.getEci());
        threeDs.setDsTransactionId(source.getDsTransactionId());
        threeDs.setThreeDsVersion(source.getThreeDsVersion());
        threeDs.setStatus(toMerchantThreeDsStatus(source));
        threeDs.setLiabilityShifted(source.getLiabilityShift() == null
                ? null : Integer.valueOf(1).equals(source.getLiabilityShift()));
        target.setThreeDsInfo(threeDs);
    }

    private String toMerchantThreeDsStatus(TransactionAuthenticationInfoDO source) {
        if ("AUTHENTICATED".equals(source.getAuthenticationStatus())) {
            return "Y";
        }
        if ("FAILED".equals(source.getAuthenticationStatus())) {
            return "N";
        }
        return Integer.valueOf(1).equals(source.getChallengeRequired()) ? "C" : "U";
    }

    private void fillFinance(MerchantTransactionResultDetailDTO target,
                             TransactionFinanceStateDO source) {
        if (source == null) {
            return;
        }
        target.setSettlementRate(source.getSettlementRate());
        target.setSettlementAmount(source.getSettlementAmount());
        target.setSettlementCurrency(source.getSettlementCurrency());
        target.setSettlementFeeAmount(source.getSettlementFeeAmount());
        if (StringUtils.hasText(source.getFeeItemsJson())) {
            List<MerchantTransactionResultDetailDTO.FeeItemDTO> feeItems = JsonUtils.parseArray(
                    source.getFeeItemsJson(), MerchantTransactionResultDetailDTO.FeeItemDTO.class);
            target.setFeeItems(feeItems == null ? List.of() : feeItems);
        }
    }
}
