package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.link.entity.PaymentLinkAccessDO;

/** 访问摘要按唯一 link_id 查询，不存明文令牌。 */
public interface PaymentLinkAccessMapper extends BaseMapper<PaymentLinkAccessDO> { }
