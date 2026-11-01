package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailAccountDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户邮件发件账户 Mapper，位于 service-merchant 数据访问层；只读取管理系统维护的发件账户配置用于商户安全通知发送。
 */
@Mapper
public interface MerchantEmailAccountMapper extends BaseMapper<MerchantEmailAccountDO> {
}
