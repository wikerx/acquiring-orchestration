package com.scott.payment.admin.application.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysNoticeDO;
import com.scott.payment.component.db.auth.mapper.SysNoticeMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 后台通知公告应用服务。
 */
@Service
public class AdminNoticeApplicationService {

    private final SysNoticeMapper sysNoticeMapper;

    /**
     * 创建后台通知公告应用服务。
     *
     * @param sysNoticeMapper 通知公告 Mapper
     */
    public AdminNoticeApplicationService(SysNoticeMapper sysNoticeMapper) {
        this.sysNoticeMapper = sysNoticeMapper;
    }

    /**
     * 分页查询通知公告。
     *
     * @param pageNo      页码
     * @param pageSize    每页大小
     * @param noticeTitle 标题
     * @param noticeType  类型
     * @param createBy    创建人
     * @return 分页结果
     */
    public PageResult<SysNoticeDO> pageNotices(int pageNo, int pageSize, String noticeTitle,
                                               String noticeType, String createBy) {
        LambdaQueryWrapper<SysNoticeDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(noticeTitle), SysNoticeDO::getNoticeTitle, noticeTitle);
        queryWrapper.eq(StringUtils.hasText(noticeType), SysNoticeDO::getNoticeType, noticeType);
        queryWrapper.like(StringUtils.hasText(createBy), SysNoticeDO::getCreateBy, createBy);
        queryWrapper.eq(SysNoticeDO::getDeleted, AuthConstants.NOT_DELETED);
        queryWrapper.orderByDesc(SysNoticeDO::getCreatedAt);

        Page<SysNoticeDO> page = sysNoticeMapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 查询通知公告详情。
     *
     * @param id 主键
     * @return 通知公告
     */
    public SysNoticeDO getNotice(Long id) {
        return sysNoticeMapper.selectById(id);
    }

    /**
     * 新增通知公告。
     *
     * @param notice 通知公告实体
     * @return 保存后的实体
     */
    public SysNoticeDO createNotice(SysNoticeDO notice) {
        notice.setId(null);
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        notice.setDeleted(0L);
        sysNoticeMapper.insert(notice);
        return notice;
    }

    /**
     * 更新通知公告。
     *
     * @param id     主键
     * @param notice 更新输入
     * @return 更新后的实体
     */
    public SysNoticeDO updateNotice(Long id, SysNoticeDO notice) {
        notice.setId(id);
        notice.setUpdatedAt(LocalDateTime.now());
        sysNoticeMapper.updateById(notice);
        return notice;
    }

    /**
     * 逻辑删除通知公告。
     *
     * @param id 主键
     */
    public void removeNotice(Long id) {
        SysNoticeDO notice = sysNoticeMapper.selectById(id);
        if (notice != null) {
            notice.setDeleted(id);
            notice.setUpdatedAt(LocalDateTime.now());
            sysNoticeMapper.updateById(notice);
        }
    }
}
