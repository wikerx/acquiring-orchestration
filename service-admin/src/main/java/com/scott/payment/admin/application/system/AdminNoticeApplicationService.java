package com.scott.payment.admin.application.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminNoticeApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台通知公告应用服务
 * @status : create
 */
@Service
public class AdminNoticeApplicationService {

    /**
     * sys Notice Mapper 依赖，用于 Admin Notice Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
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
        return findNotice(id);
    }

    /**
     * 查询工作台展示的启用公告。
     *
     * @param limit 最大条数
     * @return 启用公告列表
     */
    public List<SysNoticeDO> listDashboardNotices(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5));
        return sysNoticeMapper.selectPage(new Page<>(1, safeLimit), new LambdaQueryWrapper<SysNoticeDO>()
                .eq(SysNoticeDO::getDeleted, AuthConstants.NOT_DELETED)
                .eq(SysNoticeDO::getStatus, 1)
                .orderByDesc(SysNoticeDO::getCreatedAt)).getRecords();
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
        notice.setCreateBy(currentOperatorName());
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
        SysNoticeDO existing = findNotice(id);
        existing.setNoticeTitle(notice.getNoticeTitle());
        existing.setNoticeType(notice.getNoticeType());
        existing.setNoticeContent(notice.getNoticeContent());
        existing.setStatus(notice.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        sysNoticeMapper.updateById(existing);
        return existing;
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
     * 查询公告，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
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
     * 整理当前操作人名称，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
