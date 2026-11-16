package com.scott.payment.admin.client.payment.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/** service-admin 与 service-payment 之间的系统交易加黑内部契约。 */
public final class TradeBlackPaymentClientDTOs {

    private TradeBlackPaymentClientDTOs() {
    }

    /** 批量交易事实查询命令。 */
    @Data
    public static class ResolveCommand {
        private List<Reference> references;
    }

    /** 单笔交易定位引用。 */
    @Data
    public static class Reference {
        private Integer rowNo;
        private String identifierType;
        private String lookupMerchantId;
        private String orderNo;
    }

    /** 批量事实响应。 */
    @Data
    public static class FactsResult {
        private List<FactItem> items = new ArrayList<>();
    }

    /** 单笔交易事实或定位失败结果。 */
    @Data
    public static class FactItem {
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

    /** 联系人和地址事实。 */
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

    /** 付款人事实。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Payer extends PersonAddress {
        private String payerId;
        private String ipAddress;
    }
}
