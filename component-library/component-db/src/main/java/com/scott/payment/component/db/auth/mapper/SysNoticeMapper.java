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
 * @description : SysNoticeMapper MyBatis 数据访问接口，用于映射数据库表读写语句和领域查询条件，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface SysNoticeMapper extends BaseMapper<SysNoticeDO> {
}
