package com.scott.payment.payment.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.TradeBlackTransactionFactsDTO;
import com.scott.payment.payment.api.internal.dto.TradeBlackTransactionResolveCommandDTO;
import com.scott.payment.payment.entity.TransactionBillingInfoDO;
import com.scott.payment.payment.entity.TransactionLocatorDO;
import com.scott.payment.payment.entity.TransactionPayerInfoDO;
import com.scott.payment.payment.entity.TransactionPaymentMethodInfoDO;
import com.scott.payment.payment.entity.TransactionShippingInfoDO;
import com.scott.payment.payment.mapper.TransactionBillingInfoMapper;
import com.scott.payment.payment.mapper.TransactionLocatorMapper;
import com.scott.payment.payment.mapper.TransactionPayerInfoMapper;
import com.scott.payment.payment.mapper.TransactionPaymentMethodInfoMapper;
import com.scott.payment.payment.mapper.TransactionShippingInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 为管理端系统交易加黑解析交易事实。
 *
 * <p>该服务只负责可信交易快照读取，不计算名单哈希，也不执行名单变更。调用入口受
 * {@code /internal/**} HMAC 拦截器保护，且实现中禁止记录请求和返回的个人信息。</p>
 */
@Service
public class TradeBlackTransactionFactApplicationService {

    private final TransactionLocatorMapper locatorMapper;
    private final TransactionPaymentMethodInfoMapper paymentMethodInfoMapper;
    private final TransactionBillingInfoMapper billingInfoMapper;
    private final TransactionPayerInfoMapper payerInfoMapper;
    private final TransactionShippingInfoMapper shippingInfoMapper;

    public TradeBlackTransactionFactApplicationService(TransactionLocatorMapper locatorMapper,
                                                        TransactionPaymentMethodInfoMapper paymentMethodInfoMapper,
                                                        TransactionBillingInfoMapper billingInfoMapper,
                                                        TransactionPayerInfoMapper payerInfoMapper,
                                                        TransactionShippingInfoMapper shippingInfoMapper) {
        this.locatorMapper = locatorMapper;
        this.paymentMethodInfoMapper = paymentMethodInfoMapper;
        this.billingInfoMapper = billingInfoMapper;
        this.payerInfoMapper = payerInfoMapper;
        this.shippingInfoMapper = shippingInfoMapper;
    }

    /** 批量解析交易事实；不存在或定位条件不完整的行以行级错误返回。 */
    @DS(DataSourceName.TRANSACTION)
    public TradeBlackTransactionFactsDTO resolve(TradeBlackTransactionResolveCommandDTO command) {
        TradeBlackTransactionFactsDTO result = new TradeBlackTransactionFactsDTO();
        for (TradeBlackTransactionResolveCommandDTO.Reference reference : command.getReferences()) {
            result.getItems().add(resolveOne(reference));
        }
        return result;
    }

    private TradeBlackTransactionFactsDTO.Item resolveOne(TradeBlackTransactionResolveCommandDTO.Reference reference) {
        TradeBlackTransactionFactsDTO.Item item = new TradeBlackTransactionFactsDTO.Item();
        item.setRowNo(reference.getRowNo());
        item.setInputOrderNo(trim(reference.getOrderNo()));
        TransactionLocatorDO locator = locate(reference, item);
        if (locator == null) {
            return item;
        }
        String rootTransactionId = StringUtils.hasText(locator.getRootTransactionId())
                ? locator.getRootTransactionId() : locator.getTransactionId();
        LocalDateTime rootTransactionTime = locator.getRootTransactionDateTime() == null
                ? locator.getTransactionDateTime() : locator.getRootTransactionDateTime();
        item.setFound(true);
        item.setTransactionId(rootTransactionId);
        item.setMerchantId(locator.getMerchantId());
        item.setMerchantOrderNo(locator.getMerchantOrderNo());
        fillPayment(item, paymentMethodInfoMapper.selectByTransactionId(rootTransactionId, rootTransactionTime));
        item.setBilling(toPersonAddress(billingInfoMapper.selectByTransaction(rootTransactionId, rootTransactionTime)));
        item.setPayer(toPayer(payerInfoMapper.selectByTransaction(rootTransactionId, rootTransactionTime)));
        item.setShipping(toPersonAddress(shippingInfoMapper.selectByTransaction(rootTransactionId, rootTransactionTime)));
        return item;
    }

    private TransactionLocatorDO locate(TradeBlackTransactionResolveCommandDTO.Reference reference,
                                        TradeBlackTransactionFactsDTO.Item item) {
        String orderNo = trim(reference.getOrderNo());
        if (reference.getIdentifierType() == TradeBlackTransactionResolveCommandDTO.IdentifierType.MERCHANT_ORDER_NO) {
            String merchantId = trim(reference.getLookupMerchantId());
            if (!StringUtils.hasText(merchantId)) {
                item.setErrorCode("LOOKUP_MERCHANT_REQUIRED");
                item.setErrorMessage("按商户订单号查询时必须指定来源商户");
                return null;
            }
            TransactionLocatorDO locator = locatorMapper.selectRootByMerchantOrder(merchantId, orderNo);
            if (locator == null) {
                notFound(item);
            }
            return locator;
        }
        TransactionLocatorDO locator = locatorMapper.selectByTransactionIdForAdmin(orderNo);
        if (locator == null) {
            notFound(item);
        }
        return locator;
    }

    private void fillPayment(TradeBlackTransactionFactsDTO.Item target, TransactionPaymentMethodInfoDO source) {
        if (source == null) {
            return;
        }
        target.setPaymentAccountHash(source.getPaymentAccountHash());
        target.setCardNumberMasked(source.getCardNumberMasked());
        target.setCardBin(source.getCardBin());
        target.setPaymentBrand(source.getPaymentBrand());
        target.setIssuerCountry(source.getIssuerCountry());
    }

    private TradeBlackTransactionFactsDTO.PersonAddress toPersonAddress(TransactionBillingInfoDO source) {
        if (source == null) {
            return null;
        }
        TradeBlackTransactionFactsDTO.PersonAddress target = new TradeBlackTransactionFactsDTO.PersonAddress();
        copyAddress(target, source.getFirstName(), source.getLastName(), source.getEmail(), source.getPhone(),
                source.getCountry(), source.getState(), source.getCity(), source.getStreet(), source.getPostal());
        return target;
    }

    private TradeBlackTransactionFactsDTO.PersonAddress toPersonAddress(TransactionShippingInfoDO source) {
        if (source == null) {
            return null;
        }
        TradeBlackTransactionFactsDTO.PersonAddress target = new TradeBlackTransactionFactsDTO.PersonAddress();
        copyAddress(target, source.getFirstName(), source.getLastName(), source.getEmail(), source.getPhone(),
                source.getCountry(), source.getState(), source.getCity(), source.getStreet(), source.getPostal());
        return target;
    }

    private TradeBlackTransactionFactsDTO.Payer toPayer(TransactionPayerInfoDO source) {
        if (source == null) {
            return null;
        }
        TradeBlackTransactionFactsDTO.Payer target = new TradeBlackTransactionFactsDTO.Payer();
        copyAddress(target, source.getFirstName(), source.getLastName(), source.getEmail(), source.getPhone(),
                source.getCountry(), source.getState(), source.getCity(), source.getStreet(), source.getPostal());
        target.setPayerId(source.getPayerId());
        target.setIpAddress(source.getIpAddress());
        return target;
    }

    private void copyAddress(TradeBlackTransactionFactsDTO.PersonAddress target,
                             String firstName,
                             String lastName,
                             String email,
                             String phone,
                             String country,
                             String state,
                             String city,
                             String street,
                             String postal) {
        target.setFirstName(firstName);
        target.setLastName(lastName);
        target.setEmail(email);
        target.setPhone(phone);
        target.setCountry(country);
        target.setState(state);
        target.setCity(city);
        target.setStreet(street);
        target.setPostal(postal);
    }

    private void notFound(TradeBlackTransactionFactsDTO.Item item) {
        item.setErrorCode("TRANSACTION_NOT_FOUND");
        item.setErrorMessage("未找到对应交易");
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
