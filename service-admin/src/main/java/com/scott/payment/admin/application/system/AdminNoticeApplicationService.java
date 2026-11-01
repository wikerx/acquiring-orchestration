package com.scott.payment.admin.application.system;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.converter.NoticeConverter;
import com.scott.payment.admin.dto.system.NoticeDTOs.Response;
import com.scott.payment.admin.dto.system.NoticeDTOs.SaveRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysNoticeDO;
import com.scott.payment.component.db.auth.mapper.SysNoticeMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 管理后台通知公告应用服务
 */
@Service
public class AdminNoticeApplicationService {

    private final SysNoticeMapper sysNoticeMapper;
    private final NoticeConverter noticeConverter;

    /**
     * 创建后台通知公告应用服务。
     *
     * @param sysNoticeMapper 通知公告 Mapper
     * @param noticeConverter 通知公告接口模型转换器
     */
    public AdminNoticeApplicationService(SysNoticeMapper sysNoticeMapper, NoticeConverter noticeConverter) {
        this.sysNoticeMapper = sysNoticeMapper;
        this.noticeConverter = noticeConverter;
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
    @DS(DataSourceName.SLAVE)
    public PageResult<Response> pageNotices(int pageNo, int pageSize, String noticeTitle,
                                            String noticeType, String createBy) {
        LambdaQueryWrapper<SysNoticeDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(noticeTitle), SysNoticeDO::getNoticeTitle, noticeTitle);
        queryWrapper.eq(StringUtils.hasText(noticeType), SysNoticeDO::getNoticeType, noticeType);
        queryWrapper.like(StringUtils.hasText(createBy), SysNoticeDO::getCreateBy, createBy);
        queryWrapper.eq(SysNoticeDO::getDeleted, AuthConstants.NOT_DELETED);
        queryWrapper.orderByDesc(SysNoticeDO::getCreatedAt);

        Page<SysNoticeDO> page = sysNoticeMapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                noticeConverter.toResponses(page.getRecords()));
    }

    /**
     * 查询通知公告详情。
     *
     * @param id 主键
     * @return 通知公告
     */
    @DS(DataSourceName.SLAVE)
    public Response getNotice(Long id) {
        return noticeConverter.toResponse(findNotice(id));
    }

    /**
     * 查询工作台展示的启用公告。
     *
     * @param limit 最大条数
     * @return 启用公告列表
     */
    @DS(DataSourceName.SLAVE)
    public List<Response> listDashboardNotices(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5));
        List<SysNoticeDO> notices = sysNoticeMapper.selectPage(
                new Page<>(1, safeLimit), new LambdaQueryWrapper<SysNoticeDO>()
                .eq(SysNoticeDO::getDeleted, AuthConstants.NOT_DELETED)
                .eq(SysNoticeDO::getStatus, 1)
                .orderByDesc(SysNoticeDO::getCreatedAt)).getRecords();
        return noticeConverter.toResponses(notices);
    }

    /**
     * 新增通知公告。
     *
     * @param request 通知公告保存请求
     * @return 保存后的通知公告
     */
    public Response createNotice(SaveRequest request) {
        SysNoticeDO notice = noticeConverter.toEntity(request);
        notice.setId(null);
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        notice.setCreateBy(currentOperatorName());
        notice.setDeleted(0L);
        sysNoticeMapper.insert(notice);
        return noticeConverter.toResponse(notice);
    }

    /**
     * 更新通知公告。
     *
     * @param id     主键
     * @param request 更新输入
     * @return 更新后的通知公告
     */
    public Response updateNotice(Long id, SaveRequest request) {
        SysNoticeDO existing = findNotice(id);
        existing.setNoticeTitle(request.getNoticeTitle());
        existing.setNoticeType(request.getNoticeType());
        existing.setNoticeContent(request.getNoticeContent());
        existing.setStatus(request.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        sysNoticeMapper.updateById(existing);
        return noticeConverter.toResponse(existing);
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

    /**
     * 批量逻辑删除通知公告。
     *
     * @param ids 主键列表
     */
    public void removeNotices(List<Long> ids) {
        Set<Long> normalizedIds = new LinkedHashSet<>(ids == null ? List.of() : ids);
        normalizedIds.removeIf(id -> id == null || id <= 0);
        if (normalizedIds.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择需要删除的通知公告");
        }
        LocalDateTime now = LocalDateTime.now();
        sysNoticeMapper.selectList(new LambdaQueryWrapper<SysNoticeDO>()
                .eq(SysNoticeDO::getDeleted, AuthConstants.NOT_DELETED)
                .in(SysNoticeDO::getId, normalizedIds))
                .stream()
                .filter(Objects::nonNull)
                .forEach(notice -> {
                    notice.setDeleted(notice.getId());
                    notice.setUpdatedAt(now);
                    sysNoticeMapper.updateById(notice);
                });
    }

    /**
     * 查询未删除的系统公告，不存在时返回业务错误。
     */
    private SysNoticeDO findNotice(Long id) {
        SysNoticeDO notice = sysNoticeMapper.selectOne(new LambdaQueryWrapper<SysNoticeDO>()
                .eq(SysNoticeDO::getDeleted, AuthConstants.NOT_DELETED)
                .eq(SysNoticeDO::getId, id));
        if (notice == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "通知公告不存在");
        }
        return notice;
    }

    /**
     * 从可信认证上下文解析当前操作人名称。
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "system";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        if (StringUtils.hasText(account.getLoginAccount())) {
            return account.getLoginAccount();
        }
        return "system";
    }
}
