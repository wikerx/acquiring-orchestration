package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.base.CardBinEntities;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseCardBinImportBatchMapper
 * @date : 2026-07-04 00:00
 * @email : scott_x@163.com
 * @description : 卡 BIN 导入批次 Mapper，位于 service-admin 数据访问层，负责旧库初始化和导入批次记录查询。
 * @status : create
 */
public interface BaseCardBinImportBatchMapper extends BaseMapper<CardBinEntities.BaseCardBinImportBatchDO> {
}
