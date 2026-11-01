package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.BizDocumentDO;

/**
 * 通用业务资料元数据 Mapper，仅负责 biz_document 表访问，不处理文件正文和存储规则
 */
public interface BizDocumentMapper extends BaseMapper<BizDocumentDO> {
}
