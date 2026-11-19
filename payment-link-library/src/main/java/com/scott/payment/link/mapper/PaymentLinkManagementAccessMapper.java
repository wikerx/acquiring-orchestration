package com.scott.payment.link.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.link.entity.PaymentLinkAccessDO;

/** 共用同一链接表的访问摘要或脱敏事件，事务随管理命令提交。 */
public interface PaymentLinkManagementAccessMapper extends BaseMapper<PaymentLinkAccessDO> { }
