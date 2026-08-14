package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.TransactionLocatorDO;
import com.scott.payment.payment.mapper.TransactionLocatorMapper;
import com.scott.payment.payment.service.TransactionLocatorService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionLocatorService
 * @date : 2026-08-14 12:35
 * @email : scott_x@163.com
 * @description : 交易定位服务默认实现，通过 merchant_id 约束的固定表查询恢复分片路由，不向调用方暴露其他商户交易是否存在。
 * @status : create
 */
@Service
@DS(DataSourceName.TRANSACTION)
public class DefaultTransactionLocatorService implements TransactionLocatorService {

    private final TransactionLocatorMapper transactionLocatorMapper;

    /**
     * 创建交易定位服务。
     *
     * @param transactionLocatorMapper 交易定位 Mapper
     */
    public DefaultTransactionLocatorService(TransactionLocatorMapper transactionLocatorMapper) {
        this.transactionLocatorMapper = transactionLocatorMapper;
    }

    /**
     * 根据源交易 ID 补齐后续资金动作的分片路由时间。
     *
     * @param commandDTO 后续动作命令
     */
    @Override
    public void enrichFollowUpRoute(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getMerchantId())
                || commandDTO.getTransactionInfo() == null
                || !StringUtils.hasText(commandDTO.getTransactionInfo().getSourceTransactionId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo = commandDTO.getTransactionInfo();
        TransactionLocatorDO locator = transactionLocatorMapper.selectByTransactionId(
                commandDTO.getMerchantId(), transactionInfo.getSourceTransactionId());
        validateMerchantOrder(commandDTO, locator);
        transactionInfo.setRootTransactionId(locator.getRootTransactionId());
        transactionInfo.setSourceTransactionDateTime(locator.getTransactionDateTime());
        transactionInfo.setRootTransactionDateTime(locator.getRootTransactionDateTime());
    }

    /**
     * 根据平台交易 ID 或商户订单号补齐查询路由时间。
     *
     * @param commandDTO 商户交易查询命令
     */
    @Override
    public void enrichQueryRoute(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getMerchantId())
                || !StringUtils.hasText(commandDTO.getMerchantOrderNo())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo = commandDTO.getTransactionInfo();
        if (transactionInfo == null) {
            transactionInfo = new PaymentCreateCommandDTO.TransactionInfoDTO();
            commandDTO.setTransactionInfo(transactionInfo);
        }
        TransactionLocatorDO locator = StringUtils.hasText(transactionInfo.getTransactionId())
                ? transactionLocatorMapper.selectByTransactionId(
                        commandDTO.getMerchantId(), transactionInfo.getTransactionId())
                : transactionLocatorMapper.selectRootByMerchantOrder(
                        commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
        validateMerchantOrder(commandDTO, locator);
        transactionInfo.setRootTransactionId(locator.getRootTransactionId());
        transactionInfo.setSourceTransactionDateTime(locator.getTransactionDateTime());
        transactionInfo.setRootTransactionDateTime(locator.getRootTransactionDateTime());
    }

    /** 商户不匹配和记录不存在统一返回订单不存在，避免交易标识枚举。 */
    private void validateMerchantOrder(PaymentCreateCommandDTO commandDTO, TransactionLocatorDO locator) {
        if (locator == null
                || !commandDTO.getMerchantId().equals(locator.getMerchantId())
                || !commandDTO.getMerchantOrderNo().equals(locator.getMerchantOrderNo())
                || locator.getTransactionDateTime() == null
                || locator.getRootTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
    }
}
