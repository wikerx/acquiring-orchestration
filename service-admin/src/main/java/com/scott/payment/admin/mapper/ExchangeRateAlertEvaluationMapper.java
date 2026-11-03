package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.exchange.ExchangeRateAlertEntities.ExchangeRateAlertEvaluationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

/** 汇率波动预警评估幂等记录数据访问接口。 */
public interface ExchangeRateAlertEvaluationMapper extends BaseMapper<ExchangeRateAlertEvaluationDO> {

    /**
     * 尝试登记一轮评估；唯一 request_key 使重复任务只能有一个执行者。
     *
     * @param row 初始评估记录
     * @return 1 表示取得执行权，0 表示 requestKey 已存在
     */
    @Insert("""
            INSERT IGNORE INTO exchange_rate_alert_evaluation (
                request_key, window_end_time, evaluation_status,
                evaluated_rule_count, evaluated_pair_count, triggered_event_count,
                updated_event_count, resolved_event_count, insufficient_data_count,
                start_time, create_time
            ) VALUES (
                #{requestKey}, #{windowEndTime}, #{evaluationStatus},
                0, 0, 0, 0, 0, 0, #{startTime}, #{createTime}
            )
            """)
    int insertIgnore(ExchangeRateAlertEvaluationDO row);

    /**
     * 按幂等请求键将本轮评估从处理中原子推进为成功，并持久化完整统计。
     *
     * @param row 已完成的评估结果
     * @return 1 表示完成态写入成功，0 表示记录不存在或状态已被其他执行者改变
     */
    @Update("""
            UPDATE exchange_rate_alert_evaluation
            SET evaluation_status = 'SUCCESS',
                evaluated_rule_count = #{evaluatedRuleCount},
                evaluated_pair_count = #{evaluatedPairCount},
                triggered_event_count = #{triggeredEventCount},
                updated_event_count = #{updatedEventCount},
                resolved_event_count = #{resolvedEventCount},
                insufficient_data_count = #{insufficientDataCount},
                error_message = NULL,
                end_time = #{endTime}
            WHERE request_key = #{requestKey}
              AND evaluation_status = 'PROCESSING'
            """)
    int completeProcessing(ExchangeRateAlertEvaluationDO row);
}
