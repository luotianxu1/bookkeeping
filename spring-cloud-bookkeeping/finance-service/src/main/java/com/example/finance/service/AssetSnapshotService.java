package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.AccountResponse;
import com.example.finance.dto.AssetAccountSnapshotItemResponse;
import com.example.finance.dto.AssetAccountSnapshotResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.AssetDailySnapshotEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.AssetDailySnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AssetSnapshotService {

    private static final String ACTIVE_STATUS = "active";

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final AssetDailySnapshotMapper assetDailySnapshotMapper;
    private final AccountService accountService;

    public AssetSnapshotService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        AssetDailySnapshotMapper assetDailySnapshotMapper,
        AccountService accountService
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
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
            List<AccountResponse> activeAccounts = accountService.listNetWorthAccounts(userId, ACTIVE_STATUS);
            BigDecimal totalAssets = accountService.calculateTotalAssets(activeAccounts);
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

    public AssetAccountSnapshotResponse getLatestAccountSnapshots(Long userId) {
        LocalDate snapshotDate = LocalDate.now().minusDays(1);
        AssetAccountSnapshotResponse response = new AssetAccountSnapshotResponse();
        response.setUserId(userId);
        response.setSnapshotDate(snapshotDate);
        response.setTotalAssets(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setCurrentTotalAssets(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setChangeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setAccounts(Collections.emptyList());
        if (userId == null || userId <= 0) {
            return response;
        }

        List<AccountResponse> currentAccounts = accountService.listNetWorthAccounts(userId, ACTIVE_STATUS);
        Map<Long, BigDecimal> currentAssetsByAccountId = currentAccounts.stream()
            .collect(Collectors.toMap(
                AccountResponse::getId,
                accountService::resolveSignedNetWorthBalance
            ));
        Map<Long, AccountResponse> currentAccountById = currentAccounts.stream()
            .collect(Collectors.toMap(AccountResponse::getId, item -> item));
        BigDecimal currentTotalAssets = accountService.calculateTotalAssets(currentAccounts);
        response.setCurrentTotalAssets(currentTotalAssets);

        List<AssetDailySnapshotEntity> snapshotRows = assetDailySnapshotMapper.selectList(new LambdaQueryWrapper<AssetDailySnapshotEntity>()
            .eq(AssetDailySnapshotEntity::getUserId, userId)
            .eq(AssetDailySnapshotEntity::getSnapshotDate, snapshotDate)
            .orderByAsc(AssetDailySnapshotEntity::getId));
        if (snapshotRows.isEmpty()) {
            return response;
        }

        AssetDailySnapshotEntity totalSnapshot = null;
        List<AssetDailySnapshotEntity> accountSnapshots = new ArrayList<>();
        for (AssetDailySnapshotEntity snapshot : snapshotRows) {
            if (snapshot.getAccountId() != null && snapshot.getAccountId() == 0L) {
                totalSnapshot = snapshot;
                continue;
            }
            accountSnapshots.add(snapshot);
        }

        if (accountSnapshots.isEmpty()) {
            BigDecimal snapshotTotalAssets = totalSnapshot == null ? response.getTotalAssets() : defaultZero(totalSnapshot.getTotalAssets());
            response.setTotalAssets(snapshotTotalAssets);
            response.setChangeAmount(currentTotalAssets.subtract(snapshotTotalAssets).setScale(2, RoundingMode.HALF_UP));
            return response;
        }

        Map<Long, AssetDailySnapshotEntity> snapshotByAccountId = accountSnapshots.stream()
            .collect(Collectors.toMap(AssetDailySnapshotEntity::getAccountId, item -> item, (left, right) -> left));
        Set<Long> accountIds = new LinkedHashSet<>();
        currentAccounts.stream()
            .map(AccountResponse::getId)
            .filter(item -> item != null)
            .forEach(accountIds::add);
        snapshotByAccountId.keySet().stream()
            .filter(item -> item != null)
            .forEach(accountIds::add);

        List<AccountEntity> accountEntities = accountIds.isEmpty()
            ? Collections.emptyList()
            : accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
            .in(AccountEntity::getId, accountIds)
            .orderByAsc(AccountEntity::getSortOrder)
            .orderByAsc(AccountEntity::getId));
        Map<Long, AccountEntity> accountById = accountEntities.stream()
            .collect(Collectors.toMap(AccountEntity::getId, item -> item));
        Map<Long, Integer> accountOrderIndex = new LinkedHashMap<>();
        for (int index = 0; index < currentAccounts.size(); index++) {
            accountOrderIndex.put(currentAccounts.get(index).getId(), index);
        }

        Set<Long> accountTypeIds = accountEntities.stream()
            .map(AccountEntity::getAccountTypeId)
            .collect(Collectors.toSet());
        Map<Long, AccountTypeEntity> accountTypes = accountTypeIds.isEmpty()
            ? Collections.emptyMap()
            : accountTypeMapper.selectByIds(accountTypeIds).stream()
                .collect(Collectors.toMap(AccountTypeEntity::getId, item -> item));

        List<AssetAccountSnapshotItemResponse> accounts = accountIds.stream()
            .sorted(Comparator
                .comparingInt((Long item) -> accountOrderIndex.getOrDefault(item, Integer.MAX_VALUE))
                .thenComparing(item -> item == null ? Long.MAX_VALUE : item))
            .map(accountId -> {
                AccountResponse currentAccount = currentAccountById.get(accountId);
                AssetDailySnapshotEntity snapshot = snapshotByAccountId.get(accountId);
                AccountEntity account = accountById.get(accountId);
                AccountTypeEntity accountType = currentAccount != null
                    ? accountTypes.get(currentAccount.getAccountTypeId())
                    : account == null ? null : accountTypes.get(account.getAccountTypeId());

                AssetAccountSnapshotItemResponse snapshotItem = new AssetAccountSnapshotItemResponse();
                snapshotItem.setUserId(userId);
                snapshotItem.setAccountId(accountId);
                snapshotItem.setAccountName(currentAccount != null
                    ? currentAccount.getName()
                    : account == null ? "账户" + accountId : account.getName());
                snapshotItem.setAccountTypeCode(currentAccount != null
                    ? currentAccount.getAccountTypeCode()
                    : accountType == null ? null : accountType.getCode());
                snapshotItem.setAccountTypeLabel(currentAccount != null
                    ? currentAccount.getAccountTypeName()
                    : accountType == null ? "其他" : accountType.getName());
                BigDecimal snapshotAssets = snapshot == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : defaultZero(snapshot.getTotalAssets());
                BigDecimal currentAssets = currentAssetsByAccountId.getOrDefault(accountId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                snapshotItem.setTotalAssets(snapshotAssets);
                snapshotItem.setCurrentAssets(currentAssets);
                snapshotItem.setChangeAmount(currentAssets.subtract(snapshotAssets).setScale(2, RoundingMode.HALF_UP));
                return snapshotItem;
            })
            .toList();

        BigDecimal snapshotTotalAssets = totalSnapshot == null
            ? accountService.calculateTotalAssetsFromSignedBalances(
                accounts.stream()
                    .map(AssetAccountSnapshotItemResponse::getTotalAssets)
                    .toList()
            )
            : defaultZero(totalSnapshot.getTotalAssets());

        response.setTotalAssets(snapshotTotalAssets);
        response.setChangeAmount(currentTotalAssets.subtract(snapshotTotalAssets).setScale(2, RoundingMode.HALF_UP));
        response.setAccounts(accounts);
        return response;
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

    public LocalDate getEarliestSnapshotDate(Long userId, Long accountId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        long normalizedAccountId = accountId == null ? 0L : accountId;
        AssetDailySnapshotEntity snapshot = assetDailySnapshotMapper.selectOne(new LambdaQueryWrapper<AssetDailySnapshotEntity>()
            .eq(AssetDailySnapshotEntity::getUserId, userId)
            .eq(AssetDailySnapshotEntity::getAccountId, normalizedAccountId)
            .orderByAsc(AssetDailySnapshotEntity::getSnapshotDate)
            .orderByAsc(AssetDailySnapshotEntity::getId)
            .last("LIMIT 1"));
        return snapshot == null ? null : snapshot.getSnapshotDate();
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
