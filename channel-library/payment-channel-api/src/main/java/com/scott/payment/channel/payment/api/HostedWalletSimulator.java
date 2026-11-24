package com.scott.payment.channel.payment.api;

import java.math.BigDecimal;

/** dev 环境渠道挡板的钱包托管支付能力，不参与真实资金渠道下单。 */
public interface HostedWalletSimulator {
    record Order(String channelBaseUrl, String apiKey, String channelMerchantId, String number,
                 BigDecimal amount, String currency, String method) {
        @Override
        public String toString() {
            return "HostedWalletOrder[number=" + number + ", method=" + method + "]";
        }
    }

    enum State { READY, APPROVED, PROCESSING, SUCCESS, FAILED, CANCELLED, EXPIRED, UNKNOWN }

    record Result(State state, String transactionId, String redirectUrl, String responseCode) {
        @Override
        public String toString() {
            return "HostedWalletResult[state=" + state + ", responseCode=" + responseCode + "]";
        }
    }

    boolean supportsRoute(String channelCode, String requestUrl, String midNo);

    Result prepare(Order order, String returnUrl, String notifyUrl);

    Result query(Order order);

    Result submitApproved(Order order);
}
