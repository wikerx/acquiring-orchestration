package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.model.ReserveActionCursor;

/**
 * 保存单次保证金导出执行的续读位置及首批记录总数。
 */
final class ReserveExportPagination {

    private ReserveActionCursor afterAction;
    private long total;

    ReserveActionCursor afterAction() {
        return afterAction;
    }

    long advance(long reportedTotal, ReserveActionCursor nextAction) {
        if (afterAction == null) {
            total = reportedTotal;
        }
        afterAction = nextAction;
        return total;
    }
}
