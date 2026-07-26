package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysNoticeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysNoticeMapper
 * @date : 2026-06-12 17:37
 * @email : scott_x@163.com
 * @description : 通知公告 MyBatis Plus Mapper 接口
 * @status : create
 */

@Mapper
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysNoticeMapper
 * @date : 2026-06-12 17:37
 * @email : scott_x@163.com
 * @description : Sys Notice Mapper 映射组件，位于 公共组件库，在数据库记录、领域模型、接口 DTO 或渠道协议对象之间转换字段。
 * @status : create
 */
public interface SysNoticeMapper extends BaseMapper<SysNoticeDO> {
}
