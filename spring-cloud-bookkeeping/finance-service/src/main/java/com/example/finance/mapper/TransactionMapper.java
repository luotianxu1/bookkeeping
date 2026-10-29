package com.example.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.finance.dto.MergedTransactionRow;
import com.example.finance.entity.TransactionEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TransactionMapper extends BaseMapper<TransactionEntity> {

    /**
     * 三表 UNION ALL 后由数据库完成排序与分页。
     * type 过滤：transaction 按 type 精确匹配；debt/human 分支在 type 为空或非 transfer 时参与，
     * 并按与内存合并版一致的规则映射 record_type/direction。
     */
    List<MergedTransactionRow> selectMergedPage(
        @Param("userIds") List<Long> userIds,
        @Param("type") String type,
        @Param("accountId") Long accountId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("cashAccountIds") Set<Long> cashAccountIds,
        @Param("sortOrder") String sortOrder,
        @Param("offset") int offset,
        @Param("pageSize") int pageSize
    );

    /** 与 selectMergedPage 完全相同的过滤条件下的总条数 */
    long countMerged(
        @Param("userIds") List<Long> userIds,
        @Param("type") String type,
        @Param("accountId") Long accountId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("cashAccountIds") Set<Long> cashAccountIds
    );

    /** 与 selectMergedPage 完全相同过滤条件下的全量收入/支出合计（不分页口径，与旧内存版一致） */
    Map<String, Object> sumMerged(
        @Param("userIds") List<Long> userIds,
        @Param("type") String type,
        @Param("accountId") Long accountId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("cashAccountIds") Set<Long> cashAccountIds
    );
}
