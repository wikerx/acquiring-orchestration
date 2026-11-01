package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.base.CardBinEntities;

/**
 * 卡 BIN 导入批次 Mapper，位于 service-admin 数据访问层，负责旧库初始化和导入批次记录查询。
 */
public interface BaseCardBinImportBatchMapper extends BaseMapper<CardBinEntities.BaseCardBinImportBatchDO> {
}
