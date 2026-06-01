package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.LiabilityAccountSummaryResponse;
import com.example.finance.dto.LiabilityPrepaymentRequest;
import com.example.finance.dto.LiabilityRepaymentRequest;
import com.example.finance.dto.LiabilityRecordRequest;
import com.example.finance.dto.LiabilityRecordResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.LiabilityRecordEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.LiabilityRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class LiabilityAccountService {

    private static final String ACTIVE_STATUS = "active";
    private static final String VOIDED_STATUS = "voided";
    private static final String REPAYMENT_STATUS_PENDING = "pending";
    private static final String REPAYMENT_STATUS_PAID = "paid";
    private static final String REPAYMENT_TYPE_MONTHLY = "monthly";
    private static final String REPAYMENT_TYPE_PREPAYMENT = "prepayment";
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String LIABILITY_ACCOUNT_CODE = "liability";

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final LiabilityRecordMapper liabilityRecordMapper;

    public LiabilityAccountService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        LiabilityRecordMapper liabilityRecordMapper
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.liabilityRecordMapper = liabilityRecordMapper;
    }

    public LiabilityAccountSummaryResponse summary(Long userId, Long accountId) {
        List<AccountEntity> accounts = accountId == null
            ? loadLiabilityAccounts(userId)
            : List.of(requireLiabilityAccount(userId, accountId));
        List<LiabilityRecordEntity> records = loadLiabilityRecords(userId, accountId);
        if (accounts.isEmpty()) {
            return emptySummary();
        }

        LiabilityAccountSummaryResponse response = new LiabilityAccountSummaryResponse();
        response.setTotalAmount(sumRemainingAmounts(accounts, records));
        response.setAccountCount(accounts.size());
        response.setRecordCount(records.size());
        return response;
    }

    public List<LiabilityRecordResponse> listRecords(Long userId, Long accountId) {
        List<LiabilityRecordEntity> records = loadLiabilityRecords(userId, accountId);
        if (records.isEmpty()) {
            return List.of();
        }

        return records.stream()
            .map(record -> toResponse(record, accountMapper.selectById(record.getAccountId())))
            .toList();
    }

    @Transactional
    public LiabilityRecordResponse createRecord(LiabilityRecordRequest request) {
        AccountEntity account = requireLiabilityAccount(request.getUserId(), request.getAccountId());
        if (account.getLoanSettledAt() != null) {
            throw new IllegalArgumentException("该负债账户已结清，不能继续新增月账单");
        }
        InstallmentPlan installmentPlan = buildCreateInstallmentPlan(
            account,
            request.getAmount(),
            request.getInstallmentTotalPeriods(),
            request.getInstallmentCurrentPeriod(),
            request.getOccurredAt()
        );

        LiabilityRecordEntity entity = new LiabilityRecordEntity();
        entity.setUserId(request.getUserId());
        entity.setAccountId(account.getId());
        entity.setAmount(installmentPlan.amount());
        entity.setInstallmentTotalPeriods(installmentPlan.totalPeriods());
        entity.setInstallmentCurrentPeriod(installmentPlan.currentPeriod());
        entity.setRepaymentStatus(REPAYMENT_STATUS_PENDING);
        entity.setRepaymentType(REPAYMENT_TYPE_MONTHLY);
        entity.setPaidAt(null);
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setRemark(request.getRemark());
        entity.setOccurredAt(installmentPlan.occurredAt());
        entity.setStatus(ACTIVE_STATUS);
        liabilityRecordMapper.insert(entity);

        return toResponse(liabilityRecordMapper.selectById(entity.getId()), account);
    }

    @Transactional
    public Optional<LiabilityRecordResponse> updateRecord(Long id, LiabilityRecordRequest request) {
        LiabilityRecordEntity entity = liabilityRecordMapper.selectById(id);
        if (entity == null || !ACTIVE_STATUS.equals(entity.getStatus()) || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }
        if (REPAYMENT_TYPE_PREPAYMENT.equals(entity.getRepaymentType())) {
            throw new IllegalArgumentException("提前还款记录不支持修改");
        }

        AccountEntity account = requireLiabilityAccount(request.getUserId(), request.getAccountId());
        InstallmentPlan installmentPlan = buildUpdateInstallmentPlan(
            entity,
            account,
            request.getAmount(),
            request.getInstallmentTotalPeriods(),
            request.getInstallmentCurrentPeriod(),
            request.getOccurredAt()
        );
        entity.setAccountId(account.getId());
        entity.setAmount(installmentPlan.amount());
        entity.setInstallmentTotalPeriods(installmentPlan.totalPeriods());
        entity.setInstallmentCurrentPeriod(installmentPlan.currentPeriod());
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setRemark(request.getRemark());
        entity.setOccurredAt(installmentPlan.occurredAt());
        liabilityRecordMapper.updateById(entity);

        return Optional.of(toResponse(liabilityRecordMapper.selectById(id), account));
    }

    @Transactional
    public Optional<LiabilityRecordResponse> repayRecord(Long id, LiabilityRepaymentRequest request) {
        LiabilityRecordEntity entity = liabilityRecordMapper.selectById(id);
        if (entity == null || !ACTIVE_STATUS.equals(entity.getStatus()) || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }
        if (REPAYMENT_TYPE_PREPAYMENT.equals(entity.getRepaymentType())) {
            throw new IllegalArgumentException("提前还款记录无需重复还款");
        }

        AccountEntity account = requireLiabilityAccount(request.getUserId(), entity.getAccountId());
        entity.setRepaymentStatus(REPAYMENT_STATUS_PAID);
        entity.setPaidAt(request.getPaidAt() != null ? request.getPaidAt() : LocalDateTime.now());
        liabilityRecordMapper.updateById(entity);
        return Optional.of(toResponse(liabilityRecordMapper.selectById(id), account));
    }

    @Transactional
    public void prepayAccount(Long accountId, LiabilityPrepaymentRequest request) {
        AccountEntity account = requireLiabilityAccount(request.getUserId(), accountId);
        if (!hasLoanPlan(account)) {
            throw new IllegalArgumentException("当前负债账户尚未配置完整贷款合同");
        }
        if (account.getLoanSettledAt() != null) {
            throw new IllegalArgumentException("该负债账户已经结清");
        }

        LocalDateTime paidAt = request.getPaidAt() != null ? request.getPaidAt() : LocalDateTime.now();
        List<LiabilityRecordEntity> records = loadLiabilityRecords(request.getUserId(), accountId);
        BigDecimal remainingAmount = calculateRemainingAmount(account, records);
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("当前负债已无待还金额");
        }

        BigDecimal pendingAmount = BigDecimal.ZERO;
        for (LiabilityRecordEntity record : records) {
            if (!REPAYMENT_STATUS_PAID.equals(record.getRepaymentStatus())) {
                pendingAmount = pendingAmount.add(record.getAmount() == null ? BigDecimal.ZERO : record.getAmount());
                record.setRepaymentStatus(REPAYMENT_STATUS_PAID);
                record.setPaidAt(paidAt);
                liabilityRecordMapper.updateById(record);
            }
        }

        BigDecimal additionalPrepaymentAmount = remainingAmount.subtract(pendingAmount).setScale(2, RoundingMode.HALF_UP);
        if (additionalPrepaymentAmount.compareTo(BigDecimal.ZERO) > 0) {
            LiabilityRecordEntity settlementRecord = new LiabilityRecordEntity();
            settlementRecord.setUserId(request.getUserId());
            settlementRecord.setAccountId(account.getId());
            settlementRecord.setAmount(additionalPrepaymentAmount);
            settlementRecord.setInstallmentTotalPeriods(account.getLoanTotalPeriods());
            settlementRecord.setInstallmentCurrentPeriod(Math.min(resolveDisplayPeriod(account.getId()), account.getLoanTotalPeriods()));
            settlementRecord.setRepaymentStatus(REPAYMENT_STATUS_PAID);
            settlementRecord.setRepaymentType(REPAYMENT_TYPE_PREPAYMENT);
            settlementRecord.setPaidAt(paidAt);
            settlementRecord.setCurrencyCode(StringUtils.hasText(account.getCurrencyCode()) ? account.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
            settlementRecord.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark() : "提前还款结清");
            settlementRecord.setOccurredAt(paidAt);
            settlementRecord.setStatus(ACTIVE_STATUS);
            liabilityRecordMapper.insert(settlementRecord);
        }

        account.setLoanSettledAt(paidAt);
        accountMapper.updateById(account);
    }

    @Transactional
    public boolean deleteRecord(Long id, Long userId) {
        LiabilityRecordEntity entity = liabilityRecordMapper.selectById(id);
        if (entity == null || !ACTIVE_STATUS.equals(entity.getStatus()) || !userId.equals(entity.getUserId())) {
            return false;
        }
        if (REPAYMENT_TYPE_PREPAYMENT.equals(entity.getRepaymentType())) {
            throw new IllegalArgumentException("提前还款记录不支持删除");
        }

        entity.setStatus(VOIDED_STATUS);
        liabilityRecordMapper.updateById(entity);
        return true;
    }

    private List<AccountEntity> loadLiabilityAccounts(Long userId) {
        AccountTypeEntity type = loadLiabilityType();
        if (type == null) {
            return Collections.emptyList();
        }
        return accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
            .eq(userId != null, AccountEntity::getUserId, userId)
            .eq(AccountEntity::getAccountTypeId, type.getId())
            .eq(AccountEntity::getStatus, ACTIVE_STATUS)
            .orderByAsc(AccountEntity::getSortOrder)
            .orderByAsc(AccountEntity::getId));
    }

    private AccountTypeEntity loadLiabilityType() {
        return accountTypeMapper.selectOne(new LambdaQueryWrapper<AccountTypeEntity>()
            .eq(AccountTypeEntity::getCode, LIABILITY_ACCOUNT_CODE)
            .eq(AccountTypeEntity::getStatus, ACTIVE_STATUS)
            .last("LIMIT 1"));
    }

    private List<LiabilityRecordEntity> loadLiabilityRecords(Long userId, Long accountId) {
        return liabilityRecordMapper.selectList(new LambdaQueryWrapper<LiabilityRecordEntity>()
            .eq(userId != null, LiabilityRecordEntity::getUserId, userId)
            .eq(accountId != null, LiabilityRecordEntity::getAccountId, accountId)
            .eq(LiabilityRecordEntity::getStatus, ACTIVE_STATUS)
            .orderByDesc(LiabilityRecordEntity::getOccurredAt)
            .orderByDesc(LiabilityRecordEntity::getId));
    }

    private AccountEntity requireLiabilityAccount(Long userId, Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId()) || !ACTIVE_STATUS.equals(account.getStatus())) {
            throw new IllegalArgumentException("负债账户不存在");
        }

        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !LIABILITY_ACCOUNT_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("请选择有效的负债账户");
        }
        return account;
    }

    private BigDecimal sumRemainingAmounts(List<AccountEntity> accounts, List<LiabilityRecordEntity> records) {
        return accounts.stream()
            .map(account -> calculateRemainingAmount(
                account,
                records.stream().filter(record -> account.getId().equals(record.getAccountId())).toList()
            ))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private LiabilityRecordResponse toResponse(LiabilityRecordEntity entity, AccountEntity account) {
        LiabilityRecordResponse response = new LiabilityRecordResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setAccountId(entity.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setAmount(entity.getAmount());
        response.setInstallmentTotalPeriods(entity.getInstallmentTotalPeriods());
        response.setInstallmentCurrentPeriod(entity.getInstallmentCurrentPeriod());
        response.setRepaymentStatus(entity.getRepaymentStatus());
        response.setRepaymentType(entity.getRepaymentType());
        response.setPaidAt(entity.getPaidAt());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setRemark(entity.getRemark());
        response.setOccurredAt(entity.getOccurredAt());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private LiabilityAccountSummaryResponse emptySummary() {
        LiabilityAccountSummaryResponse response = new LiabilityAccountSummaryResponse();
        response.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setAccountCount(0);
        response.setRecordCount(0);
        return response;
    }

    private InstallmentMeta normalizeInstallmentMeta(Integer totalPeriods, Integer currentPeriod) {
        if (totalPeriods == null && currentPeriod == null) {
            return new InstallmentMeta(null, null);
        }
        if (totalPeriods == null) {
            throw new IllegalArgumentException("请先填写贷款总期数");
        }
        if (totalPeriods < 2) {
            throw new IllegalArgumentException("贷款总期数至少为2");
        }
        int normalizedCurrentPeriod = currentPeriod == null ? 1 : currentPeriod;
        if (normalizedCurrentPeriod < 1 || normalizedCurrentPeriod > totalPeriods) {
            throw new IllegalArgumentException("当前还款期数必须在1到总期数之间");
        }
        return new InstallmentMeta(totalPeriods, normalizedCurrentPeriod);
    }

    private InstallmentPlan buildCreateInstallmentPlan(
        AccountEntity account,
        BigDecimal requestAmount,
        Integer requestTotalPeriods,
        Integer requestCurrentPeriod,
        LocalDateTime requestOccurredAt
    ) {
        if (hasLoanPlan(account)) {
            int currentPeriod = resolveNextInstallmentPeriod(account.getId(), account.getLoanTotalPeriods());
            LocalDateTime occurredAt = requestOccurredAt != null
                ? requestOccurredAt
                : buildScheduledOccurredAt(account.getLoanStartDate(), account.getLoanRepaymentDay(), currentPeriod);
            return new InstallmentPlan(
                requestAmount != null && requestAmount.compareTo(BigDecimal.ZERO) > 0
                    ? requestAmount.setScale(2, RoundingMode.HALF_UP)
                    : calculateCurrentAmount(totalRepaymentAmount(account), account.getLoanTotalPeriods(), currentPeriod),
                account.getLoanTotalPeriods(),
                currentPeriod,
                occurredAt
            );
        }

        LocalDateTime occurredAt = requestOccurredAt != null ? requestOccurredAt : LocalDateTime.now();
        if (requestAmount == null || requestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("请输入有效的本期待还金额");
        }
        InstallmentMeta installmentMeta = normalizeInstallmentMeta(requestTotalPeriods, requestCurrentPeriod);
        validateDuplicatePeriod(account.getId(), installmentMeta.currentPeriod(), null);
        return new InstallmentPlan(
            requestAmount.setScale(2, RoundingMode.HALF_UP),
            installmentMeta.totalPeriods(),
            installmentMeta.currentPeriod(),
            occurredAt
        );
    }

    private InstallmentPlan buildUpdateInstallmentPlan(
        LiabilityRecordEntity entity,
        AccountEntity account,
        BigDecimal requestAmount,
        Integer requestTotalPeriods,
        Integer requestCurrentPeriod,
        LocalDateTime requestOccurredAt
    ) {
        if (hasLoanPlan(account)) {
            int currentPeriod = entity.getInstallmentCurrentPeriod() == null ? 1 : entity.getInstallmentCurrentPeriod();
            int totalPeriods = entity.getInstallmentTotalPeriods() == null ? account.getLoanTotalPeriods() : entity.getInstallmentTotalPeriods();
            LocalDateTime occurredAt = requestOccurredAt != null ? requestOccurredAt : entity.getOccurredAt();
            return new InstallmentPlan(
                requestAmount != null && requestAmount.compareTo(BigDecimal.ZERO) > 0
                    ? requestAmount.setScale(2, RoundingMode.HALF_UP)
                    : calculateCurrentAmount(totalRepaymentAmount(account), totalPeriods, currentPeriod),
                totalPeriods,
                currentPeriod,
                occurredAt
            );
        }

        LocalDateTime occurredAt = requestOccurredAt != null ? requestOccurredAt : entity.getOccurredAt();
        if (requestAmount == null || requestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("请输入有效的本期待还金额");
        }
        InstallmentMeta installmentMeta = normalizeInstallmentMeta(requestTotalPeriods, requestCurrentPeriod);
        validateDuplicatePeriod(account.getId(), installmentMeta.currentPeriod(), entity.getId());
        return new InstallmentPlan(
            requestAmount.setScale(2, RoundingMode.HALF_UP),
            installmentMeta.totalPeriods(),
            installmentMeta.currentPeriod(),
            occurredAt
        );
    }

    private boolean hasLoanPlan(AccountEntity account) {
        return account != null
            && account.getLoanTotalAmount() != null
            && account.getLoanTotalPeriods() != null
            && account.getLoanRepaymentDay() != null
            && account.getLoanStartDate() != null;
    }

    private int resolveNextInstallmentPeriod(Long accountId, int totalPeriods) {
        int maxPeriod = resolveMaxInstallmentPeriod(accountId);
        int nextPeriod = maxPeriod + 1;
        if (nextPeriod > totalPeriods) {
            throw new IllegalArgumentException("该负债账户的月账单已全部生成");
        }
        return nextPeriod;
    }

    private int resolveDisplayPeriod(Long accountId) {
        return Math.max(resolveMaxInstallmentPeriod(accountId), 1);
    }

    private int resolveMaxInstallmentPeriod(Long accountId) {
        Integer maxPeriod = liabilityRecordMapper.selectList(new LambdaQueryWrapper<LiabilityRecordEntity>()
                .eq(LiabilityRecordEntity::getAccountId, accountId)
                .eq(LiabilityRecordEntity::getStatus, ACTIVE_STATUS))
            .stream()
            .map(LiabilityRecordEntity::getInstallmentCurrentPeriod)
            .filter(period -> period != null && period > 0)
            .max(Comparator.naturalOrder())
            .orElse(0);
        return maxPeriod;
    }

    private LocalDateTime buildScheduledOccurredAt(LocalDate startDate, Integer repaymentDay, int currentPeriod) {
        YearMonth scheduleMonth = YearMonth.from(startDate).plusMonths(currentPeriod - 1L);
        int dayOfMonth = Math.min(repaymentDay == null ? startDate.getDayOfMonth() : repaymentDay, scheduleMonth.lengthOfMonth());
        return scheduleMonth.atDay(dayOfMonth).atTime(9, 0);
    }

    private BigDecimal calculateCurrentAmount(BigDecimal totalRepaymentAmount, int totalPeriods, int currentPeriod) {
        BigDecimal averageAmount = totalRepaymentAmount.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP);
        if (currentPeriod >= totalPeriods) {
            return totalRepaymentAmount
                .subtract(averageAmount.multiply(BigDecimal.valueOf(totalPeriods - 1L)))
                .setScale(2, RoundingMode.HALF_UP);
        }
        return averageAmount;
    }

    private BigDecimal totalRepaymentAmount(AccountEntity account) {
        BigDecimal principal = account.getLoanTotalAmount() == null ? BigDecimal.ZERO : account.getLoanTotalAmount();
        BigDecimal interest = resolveLoanInterestAmount(account);
        return principal.add(interest).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveLoanInterestAmount(AccountEntity account) {
        if (account == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (account.getLoanInterestRate() != null) {
            BigDecimal principal = account.getLoanTotalAmount() == null ? BigDecimal.ZERO : account.getLoanTotalAmount();
            return principal
                .multiply(account.getLoanInterestRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return account.getLoanInterestAmount() == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : account.getLoanInterestAmount().setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRemainingAmount(AccountEntity account, List<LiabilityRecordEntity> records) {
        if (account == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (account.getLoanSettledAt() != null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (!hasLoanPlan(account)) {
            return records.stream()
                .filter(record -> REPAYMENT_STATUS_PENDING.equals(record.getRepaymentStatus()) || !StringUtils.hasText(record.getRepaymentStatus()))
                .map(LiabilityRecordEntity::getAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal paidAmount = records.stream()
            .filter(record -> REPAYMENT_STATUS_PAID.equals(record.getRepaymentStatus()))
            .map(LiabilityRecordEntity::getAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = totalRepaymentAmount(account).subtract(paidAmount);
        return remaining.compareTo(BigDecimal.ZERO) > 0
            ? remaining.setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateDuplicatePeriod(Long accountId, Integer currentPeriod, Long ignoredRecordId) {
        if (accountId == null || currentPeriod == null) {
            return;
        }
        LiabilityRecordEntity existingRecord = liabilityRecordMapper.selectOne(new LambdaQueryWrapper<LiabilityRecordEntity>()
            .eq(LiabilityRecordEntity::getAccountId, accountId)
            .eq(LiabilityRecordEntity::getInstallmentCurrentPeriod, currentPeriod)
            .eq(LiabilityRecordEntity::getStatus, ACTIVE_STATUS)
            .ne(ignoredRecordId != null, LiabilityRecordEntity::getId, ignoredRecordId)
            .last("LIMIT 1"));
        if (existingRecord != null) {
            throw new IllegalArgumentException("该账期账单已存在，请勿重复新增");
        }
    }

    private record InstallmentMeta(
        Integer totalPeriods,
        Integer currentPeriod
    ) {
    }

    private record InstallmentPlan(
        BigDecimal amount,
        Integer totalPeriods,
        Integer currentPeriod,
        LocalDateTime occurredAt
    ) {
    }
}
