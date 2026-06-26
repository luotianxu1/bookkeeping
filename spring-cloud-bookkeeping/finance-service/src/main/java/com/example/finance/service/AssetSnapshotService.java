package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.AccountResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AssetDailySnapshotEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AssetDailySnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AssetSnapshotService {

    private static final String ACTIVE_STATUS = "active";

    private final AccountMapper accountMapper;
    private final AssetDailySnapshotMapper assetDailySnapshotMapper;
    private final AccountService accountService;

    public AssetSnapshotService(
        AccountMapper accountMapper,
        AssetDailySnapshotMapper assetDailySnapshotMapper,
        AccountService accountService
    ) {
        this.accountMapper = accountMapper;
        this.assetDailySnapshotMapper = assetDailySnapshotMapper;
        this.accountService = accountService;
    }

    public int captureDailySnapshots(LocalDate snapshotDate) {
        LocalDate targetDate = snapshotDate == null ? LocalDate.now().minusDays(1) : snapshotDate;
        List<AccountEntity> accounts = accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
            .eq(AccountEntity::getStatus, ACTIVE_STATUS)
            .eq(AccountEntity::getIncludeInNetWorth, true));
        if (accounts.isEmpty()) {
            return 0;
        }

        Set<Long> userIds = accounts.stream()
            .map(AccountEntity::getUserId)
            .filter(userId -> userId != null && userId > 0)
            .collect(Collectors.toSet());
        int savedCount = 0;
        for (Long userId : userIds) {
            List<AccountResponse> activeAccounts = accountService.list(userId, null, ACTIVE_STATUS).stream()
                .filter(item -> Boolean.TRUE.equals(item.getIncludeInNetWorth()))
                .toList();
            BigDecimal totalAssets = activeAccounts.stream()
                .map(accountService::resolveSignedNetWorthBalance)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
            for (AccountResponse account : activeAccounts) {
                saveOrUpdateSnapshot(
                    userId,
                    account.getId(),
                    targetDate,
                    accountService.resolveSignedNetWorthBalance(account)
                );
                savedCount++;
            }
            saveOrUpdateSnapshot(userId, 0L, targetDate, totalAssets);
            savedCount++;
        }
        return savedCount;
    }

    public Map<LocalDate, BigDecimal> getTotalAssetSnapshots(Long userId, Long accountId, LocalDate startDate, LocalDate endDate) {
        if (userId == null || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return Collections.emptyMap();
        }
        long normalizedAccountId = accountId == null ? 0L : accountId;
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        assetDailySnapshotMapper.selectList(new LambdaQueryWrapper<AssetDailySnapshotEntity>()
                .eq(AssetDailySnapshotEntity::getUserId, userId)
                .eq(AssetDailySnapshotEntity::getAccountId, normalizedAccountId)
                .between(AssetDailySnapshotEntity::getSnapshotDate, startDate, endDate)
                .orderByAsc(AssetDailySnapshotEntity::getSnapshotDate)
                .orderByAsc(AssetDailySnapshotEntity::getId))
            .forEach(item -> result.put(item.getSnapshotDate(), defaultZero(item.getTotalAssets())));
        return result;
    }

    public void saveSnapshot(Long userId, Long accountId, LocalDate snapshotDate, BigDecimal totalAssets) {
        if (userId == null || snapshotDate == null) {
            return;
        }
        saveOrUpdateSnapshot(userId, accountId == null ? 0L : accountId, snapshotDate, totalAssets);
    }

    private void saveOrUpdateSnapshot(Long userId, Long accountId, LocalDate snapshotDate, BigDecimal totalAssets) {
        AssetDailySnapshotEntity existing = assetDailySnapshotMapper.selectOne(new LambdaQueryWrapper<AssetDailySnapshotEntity>()
            .eq(AssetDailySnapshotEntity::getUserId, userId)
            .eq(AssetDailySnapshotEntity::getAccountId, accountId)
            .eq(AssetDailySnapshotEntity::getSnapshotDate, snapshotDate)
            .last("LIMIT 1"));
        if (existing == null) {
            AssetDailySnapshotEntity entity = new AssetDailySnapshotEntity();
            entity.setUserId(userId);
            entity.setAccountId(accountId);
            entity.setSnapshotDate(snapshotDate);
            entity.setTotalAssets(defaultZero(totalAssets));
            assetDailySnapshotMapper.insert(entity);
            return;
        }
        existing.setTotalAssets(defaultZero(totalAssets));
        assetDailySnapshotMapper.updateById(existing);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
