package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.BizDocumentDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BizDocumentMapper
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 通用业务资料元数据 Mapper，仅负责 biz_document 表访问，不处理文件正文和存储规则
 * @status : create
 */
public interface BizDocumentMapper extends BaseMapper<BizDocumentDO> {
}
