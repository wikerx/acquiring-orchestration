package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.PaymentLinkCheckoutBindingDO;

/** link_id、collection_id、checkout_session_id 分别唯一，跨设备沿用同一会话。 */
public interface PaymentLinkCheckoutBindingMapper extends BaseMapper<PaymentLinkCheckoutBindingDO> { }
