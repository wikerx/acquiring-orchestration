package com.scott.payment.payment.api.internal.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统交易加黑使用的交易事实批量查询结果。
 *
 * <p>完整个人信息只允许存在于 service-payment 到 service-admin 的签名内部调用中，
 * 不得写入普通日志，也不得直接返回浏览器。浏览器只能接收 service-admin 生成的脱敏候选项。</p>
 */
@Data
public class TradeBlackTransactionFactsDTO {

    /** 与请求逐行对应的解析结果。 */
    private List<Item> items = new ArrayList<>();

    /** 单笔交易事实或定位错误。 */
    @Data
    public static class Item {
        private Integer rowNo;
        private String inputOrderNo;
        private boolean found;
        private String errorCode;
        private String errorMessage;
        private String transactionId;
        private String merchantId;
        private String merchantOrderNo;
        private String paymentAccountHash;
        private String cardNumberMasked;
        private String cardBin;
        private String paymentBrand;
        private String issuerCountry;
        private PersonAddress billing;
        private Payer payer;
        private PersonAddress shipping;
    }

    /** 账单或收货联系人快照。 */
    @Data
    public static class PersonAddress {
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String country;
        private String state;
        private String city;
        private String street;
        private String postal;
    }

    /** 付款人快照。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Payer extends PersonAddress {
        private String payerId;
        private String ipAddress;
    }
}
