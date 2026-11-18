package com.scott.payment.merchant.application.paymentlink;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.entity.paymentlink.PaymentLinkDeliveryDO;
import com.scott.payment.merchant.mapper.PaymentLinkDeliveryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 先持久化请求指纹，再准备独立邮件事务；崩溃后相同请求号不能换收件人或链接。
 */
@Service
public class MerchantPaymentLinkDeliveryClaimService {
    private final PaymentLinkDeliveryMapper requests;
    public MerchantPaymentLinkDeliveryClaimService(PaymentLinkDeliveryMapper requests) { this.requests = requests; }

    /**
     * 独立提交占位事实，外层失败只回退关联结果，不删除原始请求身份。
     */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void claim(PaymentLinkDeliveryDO proposed) { requests.claim(proposed); }
    /**
     * 用独立的新快照恢复已提交邮件，避免外层 REPEATABLE READ 旧快照漏读并重复准备。
     * 普通只读结束即释放连接，不在调用邮件独立事务前持有其索引间隙锁。
     */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findCommittedEmail(String merchantId, String bizNo) {
        return requests.findCommittedEmail(merchantId, bizNo);
    }
}
