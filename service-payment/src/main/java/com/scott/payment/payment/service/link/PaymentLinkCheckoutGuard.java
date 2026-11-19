package com.scott.payment.payment.service.link;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.config.PaymentLinkProperties;
import com.scott.payment.link.model.PaymentLinkStatus;
import com.scott.payment.payment.entity.PaymentLinkCheckoutBindingDO;
import com.scott.payment.link.entity.PaymentLinkDO;
import com.scott.payment.payment.mapper.PaymentLinkCheckoutBindingMapper;
import com.scott.payment.payment.mapper.PaymentLinkMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 必须在新尝试的本地准备事务中调用；和停用操作按同一链接行锁线性化。 */
@Service
public class PaymentLinkCheckoutGuard {
    private final PaymentLinkProperties properties;
    private final PaymentLinkCheckoutBindingMapper bindings;
    private final PaymentLinkMapper links;

    public PaymentLinkCheckoutGuard(PaymentLinkProperties properties, PaymentLinkCheckoutBindingMapper bindings,
                                     PaymentLinkMapper links) {
        this.properties = properties;
        this.bindings = bindings;
        this.links = links;
    }

    /** 链接持有人可能不同，结果投影不能包含上一付款人的卡片识别信息。 */
    public boolean isLinkedSession(String checkoutSessionId) {
        return properties.isStorageReady() && bindings.selectOne(Wrappers.<PaymentLinkCheckoutBindingDO>lambdaQuery()
                .eq(PaymentLinkCheckoutBindingDO::getCheckoutSessionId, checkoutSessionId)) != null;
    }

    /** 仅可信且与已发布链接冻结事实相符的命令可使用长于普通收银台的期限。 */
    public void requireOwnedDeadline(
            com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO command,
            LocalDateTime now) {
        if (!properties.isStorageReady()) throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        PaymentLinkDO link = links.lockMerchantOrder(command.getMerchantId(), command.getMerchantOrderNo());
        if (!entryAllows(link, now)
                || !java.util.Objects.equals("PAYMENT_LINK:" + link.getLinkId(), command.getMerchantRequestId())
                || !java.util.Objects.equals(link.getExpireTime(), command.getExpireTime())
                || !java.util.Objects.equals(link.getCurrency(), command.getCurrency())
                || !java.util.Objects.equals(link.getCurrencyExponent(), command.getCurrencyExponent())
                || command.getAmount() == null || link.getAmount().compareTo(command.getAmount()) != 0
                || !java.util.Objects.equals(link.getRequestFingerprint(), command.getRequestFingerprint())
                || !"PAYMENT".equals(command.getPaymentAction())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
    }

    /**
     * 会话查询只读当前入口资格，不签发停用链接的新卡资料 nonce；在途处理不调用此门禁。
     */
    @com.baomidou.dynamic.datasource.annotation.DS(com.scott.payment.component.db.constant.DataSourceName.MASTER)
    public boolean allowsNewSubmissionRead(String checkoutSessionId, LocalDateTime now) {
        if (!properties.isStorageReady()) return true;
        PaymentLinkCheckoutBindingDO binding = bindings.selectOne(Wrappers.<PaymentLinkCheckoutBindingDO>lambdaQuery()
                .eq(PaymentLinkCheckoutBindingDO::getCheckoutSessionId, checkoutSessionId));
        if (binding == null) return true;
        PaymentLinkDO link = links.selectOne(Wrappers.<PaymentLinkDO>lambdaQuery().eq(PaymentLinkDO::getLinkId, binding.getLinkId()));
        return entryAllows(link, now);
    }

    public void requireNewSubmissionAllowed(String checkoutSessionId, LocalDateTime now) {
        // 迁移前不触及新表；迁移后即使关闭入口也不能放行旧链接会话。
        if (!properties.isStorageReady()) {
            return;
        }
        PaymentLinkCheckoutBindingDO binding = bindings.selectOne(Wrappers.<PaymentLinkCheckoutBindingDO>lambdaQuery()
                .eq(PaymentLinkCheckoutBindingDO::getCheckoutSessionId, checkoutSessionId));
        if (binding == null) {
            return;
        }
        PaymentLinkDO link = links.lock(binding.getLinkId());
        if (!entryAllows(link, now)) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED.getCode(), "payment link is not payable");
        }
    }
    private boolean entryAllows(PaymentLinkDO link, LocalDateTime now) {
        return properties.isEnabled() && link != null && PaymentLinkStatus.ACTIVE.name().equals(link.getStatus())
                && link.getExpireTime() != null && link.getExpireTime().isAfter(now);
    }

}
