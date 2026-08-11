package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.AccountRequest;
import com.example.finance.dto.AccountResponse;
import com.example.finance.dto.AccountSortOrderRequest;
import com.example.finance.dto.FinanceOverviewResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.AssetDailySnapshotEntity;
import com.example.finance.entity.DebtRecordEntity;
import com.example.finance.entity.HumanRelationRecordEntity;
import com.example.finance.entity.InvestmentAutoInvestPlanEntity;
import com.example.finance.entity.InvestmentDividendRecordEntity;
import com.example.finance.entity.InvestmentPositionEntity;
import com.example.finance.entity.InvestmentTransactionEntity;
import com.example.finance.entity.LiabilityRecordEntity;
import com.example.finance.entity.RenewalSubscriptionEntity;
import com.example.finance.entity.TransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.AssetDailySnapshotMapper;
import com.example.finance.mapper.DebtRecordMapper;
import com.example.finance.mapper.HumanRelationRecordMapper;
import com.example.finance.mapper.InvestmentAutoInvestPlanMapper;
import com.example.finance.mapper.InvestmentDividendRecordMapper;
import com.example.finance.mapper.InvestmentPositionMapper;
import com.example.finance.mapper.InvestmentTransactionMapper;
import com.example.finance.mapper.LiabilityRecordMapper;
import com.example.finance.mapper.RenewalSubscriptionMapper;
import com.example.finance.mapper.TransactionMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String DEFAULT_STATUS = "active";
    private static final String ACTIVE_POSITION_STATUS = "active";
    private static final String GOLD_ACCOUNT_TYPE_CODE = "gold";
    private static final String INVESTMENT_ACCOUNT_TYPE_CODE = "investment";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String LIABILITY_ACCOUNT_TYPE_CODE = "liability";
    private static final String LIABILITY_REPAYMENT_STATUS_PENDING = "pending";
    private static final String SUBSCRIPTION_STATUS_PENDING = "pending";
    private static final String NORMAL_STATUS = "normal";
    private static final String SETTLEMENT_STATUS_PENDING = "pending";
    private static final String BUY_TRADE_TYPE = "buy";
    private static final String DEBT_DIRECTION_PAYABLE = "payable";
    private static final String DEBT_RECORD_TYPE_REPAYMENT = "repayment";
    private static final String HUMAN_RELATION_DIRECTION_OUTGOING = "outgoing";
    private static final Set<String> DEBT_ACCOUNT_TYPE_CODES = Set.of("debt");
    private static final Set<String> CONTACT_LINKED_ACCOUNT_TYPE_CODES = Set.of("debt", "human_relation");
    private static final String DEBT_RECORD_STATUS_ACTIVE = "active";
    private static final String LIABILITY_RECORD_STATUS_ACTIVE = "active";
    private static final String HUMAN_RELATION_RECORD_STATUS_ACTIVE = "active";
    private static final Set<String> ACTIVE_RENEWAL_SUBSCRIPTION_STATUSES = Set.of("active", "paused");
    private static final Set<String> POSITION_BALANCE_ACCOUNT_TYPES = Set.of("investment", "gold");

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final AssetDailySnapshotMapper assetDailySnapshotMapper;
    private final DebtRecordMapper debtRecordMapper;
    private final LiabilityRecordMapper liabilityRecordMapper;
    private final HumanRelationRecordMapper humanRelationRecordMapper;
    private final InvestmentAutoInvestPlanMapper investmentAutoInvestPlanMapper;
    private final InvestmentPositionMapper investmentPositionMapper;
    private final InvestmentTransactionMapper investmentTransactionMapper;
    private final InvestmentDividendRecordMapper investmentDividendRecordMapper;
    private final TransactionMapper transactionMapper;
    private final RenewalSubscriptionMapper renewalSubscriptionMapper;
    private final GoldPriceService goldPriceService;
    private final JdbcTemplate jdbcTemplate;

    public AccountService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        AssetDailySnapshotMapper assetDailySnapshotMapper,
        DebtRecordMapper debtRecordMapper,
        LiabilityRecordMapper liabilityRecordMapper,
        HumanRelationRecordMapper humanRelationRecordMapper,
        InvestmentAutoInvestPlanMapper investmentAutoInvestPlanMapper,
        InvestmentPositionMapper investmentPositionMapper,
        InvestmentTransactionMapper investmentTransactionMapper,
        InvestmentDividendRecordMapper investmentDividendRecordMapper,
        TransactionMapper transactionMapper,
        RenewalSubscriptionMapper renewalSubscriptionMapper,
        GoldPriceService goldPriceService,
        JdbcTemplate jdbcTemplate
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.assetDailySnapshotMapper = assetDailySnapshotMapper;
        this.debtRecordMapper = debtRecordMapper;
        this.liabilityRecordMapper = liabilityRecordMapper;
        this.humanRelationRecordMapper = humanRelationRecordMapper;
        this.investmentAutoInvestPlanMapper = investmentAutoInvestPlanMapper;
        this.investmentPositionMapper = investmentPositionMapper;
        this.investmentTransactionMapper = investmentTransactionMapper;
        this.investmentDividendRecordMapper = investmentDividendRecordMapper;
        this.transactionMapper = transactionMapper;
        this.renewalSubscriptionMapper = renewalSubscriptionMapper;
        this.goldPriceService = goldPriceService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AccountResponse> list(Long userId, Long accountTypeId, String status) {
        LambdaQueryWrapper<AccountEntity> wrapper = new LambdaQueryWrapper<AccountEntity>()
            .eq(userId != null, AccountEntity::getUserId, userId)
            .eq(accountTypeId != null, AccountEntity::getAccountTypeId, accountTypeId)
            .eq(StringUtils.hasText(status), AccountEntity::getStatus, status)
            .orderByAsc(AccountEntity::getSortOrder)
            .orderByAsc(AccountEntity::getId);

        List<AccountEntity> accounts = accountMapper.selectList(wrapper);
        return toResponses(accounts);
    }

    public Optional<AccountResponse> getById(Long id) {
        AccountEntity account = accountMapper.selectById(id);
        if (account == null) {
            return Optional.empty();
        }
        return Optional.of(toResponse(account, loadAccountType(account.getAccountTypeId())));
    }

    public FinanceOverviewResponse overview(Long userId) {
        FinanceOverviewResponse response = new FinanceOverviewResponse();
        response.setTotalAssets(calculateTotalAssets(userId, DEFAULT_STATUS));
        return response;
    }

    public BigDecimal calculateTotalAssets(Long userId, String status) {
        return calculateTotalAssets(listNetWorthAccounts(userId, status));
    }

    public BigDecimal calculateTotalAssets(List<AccountResponse> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Set<Long> accountTypeIds = accounts.stream()
            .map(AccountResponse::getAccountTypeId)
            .filter(item -> item != null)
            .collect(Collectors.toSet());
        Map<Long, AccountTypeEntity> accountTypes = accountTypeIds.isEmpty()
            ? Collections.emptyMap()
            : accountTypeMapper.selectByIds(accountTypeIds).stream()
                .collect(Collectors.toMap(AccountTypeEntity::getId, Function.identity()));
        return calculateTotalAssetsFromSignedBalances(
            accounts.stream()
                .map(account -> resolveOverviewBalance(account, accountTypes.get(account.getAccountTypeId())))
                .toList()
        );
    }

    List<AccountResponse> listNetWorthAccounts(Long userId, String status) {
        return list(userId, null, status).stream()
            .filter(item -> Boolean.TRUE.equals(item.getIncludeInNetWorth()))
            .toList();
    }

    BigDecimal calculateTotalAssetsFromSignedBalances(Collection<BigDecimal> signedBalances) {
        if (signedBalances == null || signedBalances.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return signedBalances.stream()
            .filter(balance -> balance != null)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveOverviewBalance(AccountResponse account, AccountTypeEntity accountType) {
        BigDecimal currentBalance = account == null || account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        if (accountType == null) {
            return currentBalance;
        }
        if (GOLD_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            return currentBalance.setScale(2, RoundingMode.HALF_UP);
        }
        if (CONTACT_LINKED_ACCOUNT_TYPE_CODES.contains(accountType.getCode())) {
            return currentBalance;
        }
        if ("credit".equals(accountType.getBalanceDirection())) {
            return currentBalance.negate().setScale(2, RoundingMode.HALF_UP);
        }
        return currentBalance.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveStoredSignedBalance(AccountEntity account, AccountTypeEntity accountType) {
        BigDecimal currentBalance = account == null || account.getCurrentBalance() == null
            ? BigDecimal.ZERO
            : account.getCurrentBalance();
        if (accountType != null && "credit".equals(accountType.getBalanceDirection())) {
            return currentBalance.negate().setScale(2, RoundingMode.HALF_UP);
        }
        return currentBalance.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean shouldAdjustSnapshotBaselineForManualBalanceUpdate(
        AccountEntity entity,
        AccountRequest request,
        AccountTypeEntity originalAccountType,
        AccountTypeEntity accountType
    ) {
        if (entity == null || request == null || originalAccountType == null || accountType == null) {
            return false;
        }
        if (!entity.getAccountTypeId().equals(request.getAccountTypeId())) {
            return false;
        }
        if (!Boolean.TRUE.equals(entity.getIncludeInNetWorth()) || !Boolean.TRUE.equals(request.getIncludeInNetWorth())) {
            return false;
        }
        if (request.getCurrentBalance() == null || entity.getCurrentBalance() == null) {
            return false;
        }
        if (request.getCurrentBalance().setScale(2, RoundingMode.HALF_UP)
            .compareTo(entity.getCurrentBalance().setScale(2, RoundingMode.HALF_UP)) == 0) {
            return false;
        }
        return isStoredBalanceAccountType(originalAccountType) && isStoredBalanceAccountType(accountType);
    }

    private boolean isStoredBalanceAccountType(AccountTypeEntity accountType) {
        if (accountType == null || !StringUtils.hasText(accountType.getCode())) {
            return false;
        }
        return !POSITION_BALANCE_ACCOUNT_TYPES.contains(accountType.getCode())
            && !CONTACT_LINKED_ACCOUNT_TYPE_CODES.contains(accountType.getCode())
            && !LIABILITY_ACCOUNT_TYPE_CODE.equals(accountType.getCode());
    }

    private void adjustLatestSnapshotBaseline(Long userId, Long accountId, BigDecimal signedDelta) {
        if (userId == null || accountId == null || signedDelta == null || signedDelta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        LocalDate snapshotDate = LocalDate.now().minusDays(1);
        adjustSnapshotRow(userId, accountId, snapshotDate, signedDelta);
        adjustSnapshotRow(userId, 0L, snapshotDate, signedDelta);
    }

    private void adjustSnapshotRow(Long userId, Long accountId, LocalDate snapshotDate, BigDecimal signedDelta) {
        AssetDailySnapshotEntity snapshot = assetDailySnapshotMapper.selectOne(new LambdaQueryWrapper<AssetDailySnapshotEntity>()
            .eq(AssetDailySnapshotEntity::getUserId, userId)
            .eq(AssetDailySnapshotEntity::getAccountId, accountId)
            .eq(AssetDailySnapshotEntity::getSnapshotDate, snapshotDate)
            .last("LIMIT 1"));
        if (snapshot == null) {
            return;
        }
        BigDecimal totalAssets = snapshot.getTotalAssets() == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : snapshot.getTotalAssets();
        snapshot.setTotalAssets(totalAssets.add(signedDelta).setScale(2, RoundingMode.HALF_UP));
        assetDailySnapshotMapper.updateById(snapshot);
    }

    BigDecimal resolveSignedNetWorthBalance(AccountResponse account) {
        if (account == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        AccountTypeEntity accountType = loadAccountType(account.getAccountTypeId());
        return resolveOverviewBalance(account, accountType).setScale(2, RoundingMode.HALF_UP);
    }

    public AccountResponse create(AccountRequest request) {
        AccountTypeEntity accountType = requireAccountType(request.getAccountTypeId());
        validateContactRequired(accountType, request.getContactId());
        validateDebtAccountContactUnique(accountType, request.getUserId(), request.getContactId(), null);
        validateNameUnique(request.getUserId(), request.getAccountTypeId(), request.getName(), null);

        AccountEntity entity = new AccountEntity();
        fillEntity(entity, request, accountType);
        accountMapper.insert(entity);

        return toResponse(accountMapper.selectById(entity.getId()), accountType);
    }

    @Transactional
    public Optional<AccountResponse> update(Long id, AccountRequest request) {
        AccountEntity entity = accountMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }

        AccountTypeEntity accountType = requireAccountType(request.getAccountTypeId());
        AccountTypeEntity originalAccountType = loadAccountType(entity.getAccountTypeId());
        BigDecimal originalSignedBalance = resolveStoredSignedBalance(entity, originalAccountType);
        boolean shouldAdjustSnapshotBaseline = shouldAdjustSnapshotBaselineForManualBalanceUpdate(
            entity,
            request,
            originalAccountType,
            accountType
        );
        validateContactRequired(accountType, request.getContactId());
        validateDebtAccountContactUnique(accountType, request.getUserId(), request.getContactId(), id);
        validateNameUnique(request.getUserId(), request.getAccountTypeId(), request.getName(), id);
        fillEntity(entity, request, accountType);
        accountMapper.updateById(entity);
        if (shouldAdjustSnapshotBaseline) {
            BigDecimal signedDelta = resolveStoredSignedBalance(entity, accountType).subtract(originalSignedBalance);
            adjustLatestSnapshotBaseline(entity.getUserId(), entity.getId(), signedDelta);
        }

        return Optional.of(toResponse(accountMapper.selectById(id), accountType));
    }

    @Transactional
    public void updateSortOrders(AccountSortOrderRequest request) {
        for (AccountSortOrderRequest.AccountSortOrderItem item : request.getItems()) {
            AccountEntity account = accountMapper.selectById(item.getId());
            if (account == null || !request.getUserId().equals(account.getUserId())) {
                throw new IllegalArgumentException("账户不存在");
            }
            account.setSortOrder(item.getSortOrder());
            accountMapper.updateById(account);
        }
    }

    @Transactional
    public boolean delete(Long id) {
        AccountEntity account = accountMapper.selectById(id);
        if (account == null) {
            return false;
        }

        AccountTypeEntity accountType = loadAccountType(account.getAccountTypeId());
        if (accountType != null && CASH_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            Long debtReferenceCount = debtRecordMapper.selectCount(new LambdaQueryWrapper<DebtRecordEntity>()
                .eq(DebtRecordEntity::getFundingAccountId, id)
                .eq(DebtRecordEntity::getStatus, DEBT_RECORD_STATUS_ACTIVE));
            Long humanRelationReferenceCount = isHumanRelationRecordsTableAvailable()
                ? humanRelationRecordMapper.selectCount(new LambdaQueryWrapper<HumanRelationRecordEntity>()
                    .eq(HumanRelationRecordEntity::getFundingAccountId, id)
                    .eq(HumanRelationRecordEntity::getStatus, HUMAN_RELATION_RECORD_STATUS_ACTIVE))
                : 0L;
            Long renewalReferenceCount = renewalSubscriptionMapper.selectCount(new LambdaQueryWrapper<RenewalSubscriptionEntity>()
                .eq(RenewalSubscriptionEntity::getFundingAccountId, id));
            Long autoInvestFundingReferenceCount = investmentAutoInvestPlanMapper.selectCount(new LambdaQueryWrapper<InvestmentAutoInvestPlanEntity>()
                .eq(InvestmentAutoInvestPlanEntity::getFundingAccountId, id));
            Long investmentFundingReferenceCount = investmentTransactionMapper.selectCount(new LambdaQueryWrapper<InvestmentTransactionEntity>()
                .eq(InvestmentTransactionEntity::getFundingAccountId, id));
            long photographyReferenceCount = countPhotographyOrderAccountReferences(id);
            if ((debtReferenceCount != null && debtReferenceCount > 0)
                || (humanRelationReferenceCount != null && humanRelationReferenceCount > 0)
                || (renewalReferenceCount != null && renewalReferenceCount > 0)
                || (autoInvestFundingReferenceCount != null && autoInvestFundingReferenceCount > 0)
                || (investmentFundingReferenceCount != null && investmentFundingReferenceCount > 0)
                || photographyReferenceCount > 0) {
                throw new IllegalArgumentException("该现金账户已关联投资记录、定投计划、往来记录、固定支出或工具订单，暂时不能删除");
            }
        }

        transactionMapper.delete(new LambdaQueryWrapper<TransactionEntity>()
            .eq(TransactionEntity::getAccountId, id)
            .or()
            .eq(TransactionEntity::getFromAccountId, id)
            .or()
            .eq(TransactionEntity::getToAccountId, id));
        investmentTransactionMapper.delete(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getAccountId, id));
        investmentAutoInvestPlanMapper.delete(new LambdaQueryWrapper<InvestmentAutoInvestPlanEntity>()
            .eq(InvestmentAutoInvestPlanEntity::getAccountId, id)
            .or()
            .eq(InvestmentAutoInvestPlanEntity::getFundingAccountId, id));
        investmentDividendRecordMapper.delete(new LambdaQueryWrapper<InvestmentDividendRecordEntity>()
            .eq(InvestmentDividendRecordEntity::getAccountId, id));
        investmentPositionMapper.delete(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(InvestmentPositionEntity::getAccountId, id));
        debtRecordMapper.delete(new LambdaQueryWrapper<DebtRecordEntity>()
            .eq(DebtRecordEntity::getAccountId, id));
        if (isLiabilityRecordsTableAvailable()) {
            liabilityRecordMapper.delete(new LambdaQueryWrapper<LiabilityRecordEntity>()
                .eq(LiabilityRecordEntity::getAccountId, id));
        }
        if (isHumanRelationRecordsTableAvailable()) {
            humanRelationRecordMapper.delete(new LambdaQueryWrapper<HumanRelationRecordEntity>()
                .eq(HumanRelationRecordEntity::getAccountId, id));
        }
        return accountMapper.deleteById(id) > 0;
    }

    private boolean isHumanRelationRecordsTableAvailable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'human_relation_records'",
                Integer.class
            );
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLiabilityRecordsTableAvailable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'liability_records'",
                Integer.class
            );
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private long countPhotographyOrderAccountReferences(Long accountId) {
        if (accountId == null) {
            return 0L;
        }
        try {
            Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'photography_orders'",
                Integer.class
            );
            if (tableCount == null || tableCount <= 0) {
                return 0L;
            }
            Long referenceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM photography_orders WHERE deposit_account_id = ? OR final_account_id = ?",
                Long.class,
                accountId,
                accountId
            );
            return referenceCount == null ? 0L : referenceCount;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private AccountTypeEntity requireAccountType(Long accountTypeId) {
        AccountTypeEntity accountType = accountTypeMapper.selectById(accountTypeId);
        if (accountType == null) {
            throw new IllegalArgumentException("账户类型不存在");
        }
        return accountType;
    }

    private AccountTypeEntity loadAccountType(Long accountTypeId) {
        return accountTypeMapper.selectById(accountTypeId);
    }

    private void validateNameUnique(Long userId, Long accountTypeId, String name, Long ignoredId) {
        LambdaQueryWrapper<AccountEntity> wrapper = new LambdaQueryWrapper<AccountEntity>()
            .eq(AccountEntity::getUserId, userId)
            .eq(AccountEntity::getAccountTypeId, accountTypeId)
            .eq(AccountEntity::getName, name)
            .ne(ignoredId != null, AccountEntity::getId, ignoredId)
            .last("LIMIT 1");
        if (accountMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("账户名称已存在");
        }
    }

    private void validateContactRequired(AccountTypeEntity accountType, Long contactId) {
        if (accountType != null && CONTACT_LINKED_ACCOUNT_TYPE_CODES.contains(accountType.getCode()) && contactId == null) {
            throw new IllegalArgumentException("该账户类型必须关联联系人");
        }
    }

    private void validateDebtAccountContactUnique(AccountTypeEntity accountType, Long userId, Long contactId, Long ignoredId) {
        if (accountType == null || !CONTACT_LINKED_ACCOUNT_TYPE_CODES.contains(accountType.getCode()) || contactId == null) {
            return;
        }
        AccountEntity existingAccount = accountMapper.selectOne(new LambdaQueryWrapper<AccountEntity>()
            .eq(AccountEntity::getUserId, userId)
            .eq(AccountEntity::getContactId, contactId)
            .eq(AccountEntity::getAccountTypeId, accountType.getId())
            .ne(ignoredId != null, AccountEntity::getId, ignoredId)
            .last("LIMIT 1"));
        if (existingAccount != null) {
            throw new IllegalArgumentException("该联系人已存在同类型账户");
        }
    }

    private void fillEntity(AccountEntity entity, AccountRequest request, AccountTypeEntity accountType) {
        LiabilityPlan liabilityPlan = normalizeLiabilityPlan(accountType, request);
        entity.setUserId(request.getUserId());
        entity.setAccountTypeId(request.getAccountTypeId());
        entity.setContactId(request.getContactId());
        entity.setName(request.getName());
        entity.setIcon(request.getIcon());
        entity.setColor(request.getColor());
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setCurrentBalance(accountType != null && CONTACT_LINKED_ACCOUNT_TYPE_CODES.contains(accountType.getCode())
            ? BigDecimal.ZERO
            : request.getCurrentBalance() != null ? request.getCurrentBalance() : BigDecimal.ZERO);
        entity.setLoanTotalAmount(liabilityPlan.totalAmount());
        entity.setLoanInterestRate(liabilityPlan.interestRate());
        entity.setLoanInterestAmount(liabilityPlan.interestAmount());
        entity.setLoanTotalPeriods(liabilityPlan.totalPeriods());
        entity.setLoanRepaymentDay(liabilityPlan.repaymentDay());
        entity.setLoanStartDate(liabilityPlan.startDate());
        entity.setLoanSettledAt(accountType != null && LIABILITY_ACCOUNT_TYPE_CODE.equals(accountType.getCode()) ? entity.getLoanSettledAt() : null);
        entity.setIncludeInNetWorth(request.getIncludeInNetWorth());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setRemark(request.getRemark());
    }

    private List<AccountResponse> toResponses(List<AccountEntity> accounts) {
        if (accounts.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> accountTypeIds = accounts.stream()
            .map(AccountEntity::getAccountTypeId)
            .collect(Collectors.toSet());
        Map<Long, AccountTypeEntity> accountTypes = accountTypeMapper.selectByIds(accountTypeIds).stream()
            .collect(Collectors.toMap(AccountTypeEntity::getId, Function.identity()));
        BalanceContext balanceContext = buildBalanceContext(accounts, accountTypes);

        return accounts.stream()
            .map(account -> toResponse(account, accountTypes.get(account.getAccountTypeId()), balanceContext))
            .toList();
    }

    private AccountResponse toResponse(AccountEntity entity, AccountTypeEntity accountType) {
        return toResponse(entity, accountType, null);
    }

    private AccountResponse toResponse(AccountEntity entity, AccountTypeEntity accountType, BalanceContext balanceContext) {
        AccountResponse response = new AccountResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setAccountTypeId(entity.getAccountTypeId());
        response.setContactId(entity.getContactId());
        if (accountType != null) {
            response.setAccountTypeCode(accountType.getCode());
            response.setAccountTypeName(accountType.getName());
        }
        response.setName(entity.getName());
        response.setIcon(entity.getIcon());
        response.setColor(entity.getColor());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setCurrentBalance(resolveCurrentBalance(entity, accountType, balanceContext));
        response.setLoanTotalAmount(entity.getLoanTotalAmount());
        response.setLoanInterestRate(entity.getLoanInterestRate());
        response.setLoanInterestAmount(resolveLoanInterestAmount(entity));
        response.setLoanTotalPeriods(entity.getLoanTotalPeriods());
        response.setLoanRepaymentDay(entity.getLoanRepaymentDay());
        response.setLoanStartDate(entity.getLoanStartDate());
        response.setLoanSettledAt(entity.getLoanSettledAt());
        response.setIncludeInNetWorth(entity.getIncludeInNetWorth());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private BigDecimal resolveCurrentBalance(AccountEntity entity, AccountTypeEntity accountType, BalanceContext balanceContext) {
        if (entity == null) {
            return BigDecimal.ZERO;
        }
        if (balanceContext != null) {
            BigDecimal cachedBalance = balanceContext.accountBalances().get(entity.getId());
            if (cachedBalance != null) {
                return cachedBalance;
            }
        }
        if (accountType != null && entity != null && DEBT_ACCOUNT_TYPE_CODES.contains(accountType.getCode())) {
            return resolveDebtBalance(entity.getId());
        }
        if (accountType != null && entity != null && LIABILITY_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            return resolveLiabilityBalance(entity.getId());
        }
        if (accountType != null && entity != null && "human_relation".equals(accountType.getCode())) {
            return resolveHumanRelationBalance(entity.getId());
        }
        if (accountType == null || entity == null || !POSITION_BALANCE_ACCOUNT_TYPES.contains(accountType.getCode())) {
            return entity == null || entity.getCurrentBalance() == null ? BigDecimal.ZERO : entity.getCurrentBalance();
        }
        List<InvestmentPositionEntity> positions = investmentPositionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .select(
                    InvestmentPositionEntity::getId,
                    InvestmentPositionEntity::getAccountId,
                    InvestmentPositionEntity::getHoldingQuantity,
                    InvestmentPositionEntity::getCurrentPrice,
                    InvestmentPositionEntity::getMarketValue,
                    InvestmentPositionEntity::getCostAmount,
                    InvestmentPositionEntity::getSubscriptionStatus,
                    InvestmentPositionEntity::getSubscriptionConfirmedAt,
                    InvestmentPositionEntity::getStatus
                )
                .eq(InvestmentPositionEntity::getAccountId, entity.getId())
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_POSITION_STATUS));

        if (GOLD_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            BigDecimal realtimePrice = resolvePreferredGoldPrice(positions);
            if (realtimePrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal marketValue = positions.stream()
                    .map(InvestmentPositionEntity::getHoldingQuantity)
                    .filter(value -> value != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .multiply(realtimePrice);
                return marketValue.setScale(2, RoundingMode.HALF_UP);
            }
            return positions.stream()
                .map(InvestmentPositionEntity::getMarketValue)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        }

        if (INVESTMENT_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            BigDecimal marketValue = positions.stream()
                .map(this::resolveInvestmentPositionBalance)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return marketValue
                .add(resolvePendingBuyTransactionAmount(entity.getId()))
                .setScale(2, RoundingMode.HALF_UP);
        }

        return entity.getCurrentBalance() == null ? BigDecimal.ZERO : entity.getCurrentBalance();
    }

    private BigDecimal resolveDebtBalance(Long accountId) {
        List<DebtRecordEntity> records = debtRecordMapper.selectList(new LambdaQueryWrapper<DebtRecordEntity>()
            .eq(DebtRecordEntity::getAccountId, accountId)
            .eq(DebtRecordEntity::getStatus, DEBT_RECORD_STATUS_ACTIVE));
        BigDecimal payableTotal = records.stream()
            .map(this::resolveDebtBalanceDelta)
            .filter(value -> value.compareTo(BigDecimal.ZERO) < 0)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivableTotal = records.stream()
            .map(this::resolveDebtBalanceDelta)
            .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return receivableTotal.subtract(payableTotal).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveLiabilityBalance(Long accountId) {
        if (!isLiabilityRecordsTableAvailable()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        AccountEntity account = accountMapper.selectById(accountId);
        List<LiabilityRecordEntity> records = liabilityRecordMapper.selectList(new LambdaQueryWrapper<LiabilityRecordEntity>()
            .eq(LiabilityRecordEntity::getAccountId, accountId)
            .eq(LiabilityRecordEntity::getStatus, LIABILITY_RECORD_STATUS_ACTIVE));
        if (hasLiabilityPlan(account)) {
            return resolveLiabilityRemainingAmount(account, records);
        }
        return records.stream()
            .filter(record -> LIABILITY_REPAYMENT_STATUS_PENDING.equals(record.getRepaymentStatus()) || !StringUtils.hasText(record.getRepaymentStatus()))
            .map(LiabilityRecordEntity::getAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveHumanRelationBalance(Long accountId) {
        List<HumanRelationRecordEntity> records = humanRelationRecordMapper.selectList(new LambdaQueryWrapper<HumanRelationRecordEntity>()
            .eq(HumanRelationRecordEntity::getAccountId, accountId)
            .eq(HumanRelationRecordEntity::getStatus, HUMAN_RELATION_RECORD_STATUS_ACTIVE));
        BigDecimal outgoingTotal = records.stream()
            .filter(record -> HUMAN_RELATION_DIRECTION_OUTGOING.equals(record.getDirection()))
            .map(HumanRelationRecordEntity::getAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal incomingTotal = records.stream()
            .filter(record -> !HUMAN_RELATION_DIRECTION_OUTGOING.equals(record.getDirection()))
            .map(HumanRelationRecordEntity::getAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return outgoingTotal.subtract(incomingTotal).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePreferredGoldPrice(List<InvestmentPositionEntity> positions) {
        BigDecimal cachedSpotPrice = goldPriceService.getCachedSpotPrice();
        if (cachedSpotPrice != null && cachedSpotPrice.compareTo(BigDecimal.ZERO) > 0) {
            return cachedSpotPrice.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveInvestmentPositionBalance(InvestmentPositionEntity position) {
        if (position == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (SUBSCRIPTION_STATUS_PENDING.equals(position.getSubscriptionStatus())
            && position.getSubscriptionConfirmedAt() == null) {
            return position.getCostAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : position.getCostAmount().setScale(2, RoundingMode.HALF_UP);
        }
        return position.getMarketValue() == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : position.getMarketValue().setScale(2, RoundingMode.HALF_UP);
    }

    private LiabilityPlan normalizeLiabilityPlan(AccountTypeEntity accountType, AccountRequest request) {
        if (accountType == null || !LIABILITY_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            return new LiabilityPlan(null, null, null, null, null, null);
        }

        boolean hasAnyPlanValue = request.getLoanTotalAmount() != null
            || request.getLoanTotalPeriods() != null
            || request.getLoanStartDate() != null;
        if (!hasAnyPlanValue) {
            throw new IllegalArgumentException("请填写贷款总额、贷款总期数和首期账单日期");
        }
        if (request.getLoanTotalAmount() == null) {
            throw new IllegalArgumentException("请填写贷款总额");
        }
        if (request.getLoanTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("贷款总额必须大于0");
        }
        BigDecimal interestRate = request.getLoanInterestRate() == null
            ? BigDecimal.ZERO
            : request.getLoanInterestRate().setScale(4, RoundingMode.HALF_UP);
        if (interestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("贷款利率不能小于0");
        }
        if (request.getLoanTotalPeriods() == null) {
            throw new IllegalArgumentException("请填写贷款总期数");
        }
        if (request.getLoanTotalPeriods() < 2) {
            throw new IllegalArgumentException("贷款总期数至少为2");
        }
        if (request.getLoanRepaymentDay() == null) {
            throw new IllegalArgumentException("请填写每月还款日");
        }
        if (request.getLoanRepaymentDay() < 1 || request.getLoanRepaymentDay() > 31) {
            throw new IllegalArgumentException("每月还款日必须在1到31之间");
        }
        if (request.getLoanStartDate() == null) {
            throw new IllegalArgumentException("请填写首期账单日期");
        }

        return new LiabilityPlan(
            request.getLoanTotalAmount().setScale(2, RoundingMode.HALF_UP),
            interestRate,
            null,
            request.getLoanTotalPeriods(),
            request.getLoanRepaymentDay(),
            request.getLoanStartDate()
        );
    }

    private boolean hasLiabilityPlan(AccountEntity account) {
        return account != null
            && account.getLoanTotalAmount() != null
            && account.getLoanTotalPeriods() != null
            && account.getLoanRepaymentDay() != null
            && account.getLoanStartDate() != null;
    }

    private BigDecimal resolveLiabilityRemainingAmount(AccountEntity account, List<LiabilityRecordEntity> records) {
        if (account == null || account.getLoanSettledAt() != null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal totalRepaymentAmount = account.getLoanTotalAmount()
            .add(resolveLoanInterestAmount(account));
        BigDecimal paidAmount = records.stream()
            .filter(record -> "paid".equals(record.getRepaymentStatus()))
            .map(LiabilityRecordEntity::getAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = totalRepaymentAmount.subtract(paidAmount);
        return remaining.compareTo(BigDecimal.ZERO) > 0
            ? remaining.setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
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

    private BalanceContext buildBalanceContext(List<AccountEntity> accounts, Map<Long, AccountTypeEntity> accountTypes) {
        Map<Long, BigDecimal> accountBalances = new HashMap<>();
        if (accounts.isEmpty()) {
            return new BalanceContext(accountBalances);
        }

        Map<Long, String> accountTypeCodes = accounts.stream()
            .filter(account -> accountTypes.containsKey(account.getAccountTypeId()))
            .collect(Collectors.toMap(
                AccountEntity::getId,
                account -> accountTypes.get(account.getAccountTypeId()).getCode()
            ));

        List<Long> debtAccountIds = accountTypeCodes.entrySet().stream()
            .filter(entry -> DEBT_ACCOUNT_TYPE_CODES.contains(entry.getValue()))
            .map(Map.Entry::getKey)
            .toList();
        if (!debtAccountIds.isEmpty()) {
            List<DebtRecordEntity> records = debtRecordMapper.selectList(new LambdaQueryWrapper<DebtRecordEntity>()
                .in(DebtRecordEntity::getAccountId, debtAccountIds)
                .eq(DebtRecordEntity::getStatus, DEBT_RECORD_STATUS_ACTIVE));
            Map<Long, BigDecimal> payableTotals = sumByAccount(records, DebtRecordEntity::getAccountId,
                record -> {
                    BigDecimal delta = resolveDebtBalanceDelta(record);
                    return delta.compareTo(BigDecimal.ZERO) < 0 ? delta.abs() : BigDecimal.ZERO;
                });
            Map<Long, BigDecimal> receivableTotals = sumByAccount(records, DebtRecordEntity::getAccountId,
                record -> {
                    BigDecimal delta = resolveDebtBalanceDelta(record);
                    return delta.compareTo(BigDecimal.ZERO) > 0 ? delta : BigDecimal.ZERO;
                });
            for (Long accountId : debtAccountIds) {
                BigDecimal payable = payableTotals.getOrDefault(accountId, BigDecimal.ZERO);
                BigDecimal receivable = receivableTotals.getOrDefault(accountId, BigDecimal.ZERO);
                accountBalances.put(accountId, receivable.subtract(payable).setScale(2, RoundingMode.HALF_UP));
            }
        }

        List<Long> liabilityAccountIds = accountTypeCodes.entrySet().stream()
            .filter(entry -> LIABILITY_ACCOUNT_TYPE_CODE.equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .toList();
        if (isLiabilityRecordsTableAvailable() && !liabilityAccountIds.isEmpty()) {
            Map<Long, AccountEntity> liabilityAccountsById = accounts.stream()
                .filter(account -> liabilityAccountIds.contains(account.getId()))
                .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
            List<LiabilityRecordEntity> records = liabilityRecordMapper.selectList(new LambdaQueryWrapper<LiabilityRecordEntity>()
                .in(LiabilityRecordEntity::getAccountId, liabilityAccountIds)
                .eq(LiabilityRecordEntity::getStatus, LIABILITY_RECORD_STATUS_ACTIVE));
            Map<Long, List<LiabilityRecordEntity>> recordsByAccountId = records.stream()
                .collect(Collectors.groupingBy(LiabilityRecordEntity::getAccountId));
            for (Long accountId : liabilityAccountIds) {
                AccountEntity liabilityAccount = liabilityAccountsById.get(accountId);
                List<LiabilityRecordEntity> accountRecords = recordsByAccountId.getOrDefault(accountId, List.of());
                if (hasLiabilityPlan(liabilityAccount)) {
                    accountBalances.put(accountId, resolveLiabilityRemainingAmount(liabilityAccount, accountRecords));
                    continue;
                }
                BigDecimal pendingTotal = accountRecords.stream()
                    .filter(record -> LIABILITY_REPAYMENT_STATUS_PENDING.equals(record.getRepaymentStatus()) || !StringUtils.hasText(record.getRepaymentStatus()))
                    .map(LiabilityRecordEntity::getAmount)
                    .filter(value -> value != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
                accountBalances.put(accountId, pendingTotal);
            }
        } else if (!liabilityAccountIds.isEmpty()) {
            for (Long accountId : liabilityAccountIds) {
                accountBalances.put(accountId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
        }

        List<Long> humanRelationAccountIds = accountTypeCodes.entrySet().stream()
            .filter(entry -> "human_relation".equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .toList();
        if (!humanRelationAccountIds.isEmpty()) {
            List<HumanRelationRecordEntity> records = humanRelationRecordMapper.selectList(new LambdaQueryWrapper<HumanRelationRecordEntity>()
                .in(HumanRelationRecordEntity::getAccountId, humanRelationAccountIds)
                .eq(HumanRelationRecordEntity::getStatus, HUMAN_RELATION_RECORD_STATUS_ACTIVE));
            Map<Long, BigDecimal> outgoingTotals = sumByAccount(records, HumanRelationRecordEntity::getAccountId,
                record -> HUMAN_RELATION_DIRECTION_OUTGOING.equals(record.getDirection()) ? record.getAmount() : BigDecimal.ZERO);
            Map<Long, BigDecimal> incomingTotals = sumByAccount(records, HumanRelationRecordEntity::getAccountId,
                record -> HUMAN_RELATION_DIRECTION_OUTGOING.equals(record.getDirection()) ? BigDecimal.ZERO : record.getAmount());
            for (Long accountId : humanRelationAccountIds) {
                BigDecimal outgoing = outgoingTotals.getOrDefault(accountId, BigDecimal.ZERO);
                BigDecimal incoming = incomingTotals.getOrDefault(accountId, BigDecimal.ZERO);
                accountBalances.put(accountId, outgoing.subtract(incoming).setScale(2, RoundingMode.HALF_UP));
            }
        }

        List<Long> positionAccountIds = accountTypeCodes.entrySet().stream()
            .filter(entry -> POSITION_BALANCE_ACCOUNT_TYPES.contains(entry.getValue()))
            .map(Map.Entry::getKey)
            .toList();
        if (!positionAccountIds.isEmpty()) {
            List<InvestmentPositionEntity> positions = investmentPositionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .select(
                    InvestmentPositionEntity::getId,
                    InvestmentPositionEntity::getAccountId,
                    InvestmentPositionEntity::getHoldingQuantity,
                    InvestmentPositionEntity::getCurrentPrice,
                    InvestmentPositionEntity::getMarketValue,
                    InvestmentPositionEntity::getCostAmount,
                    InvestmentPositionEntity::getSubscriptionStatus,
                    InvestmentPositionEntity::getSubscriptionConfirmedAt,
                    InvestmentPositionEntity::getStatus
                )
                .in(InvestmentPositionEntity::getAccountId, positionAccountIds)
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_POSITION_STATUS));
            Map<Long, List<InvestmentPositionEntity>> positionsByAccount = positions.stream()
                .collect(Collectors.groupingBy(InvestmentPositionEntity::getAccountId));
            Map<Long, BigDecimal> pendingBuyAmountsByAccount = loadPendingBuyAmountsByAccount(positionAccountIds);
            BigDecimal realtimeGoldPrice = resolvePreferredGoldPrice(positions);
            for (Long accountId : positionAccountIds) {
                List<InvestmentPositionEntity> accountPositions = positionsByAccount.getOrDefault(accountId, List.of());
                String accountTypeCode = accountTypeCodes.get(accountId);
                if (GOLD_ACCOUNT_TYPE_CODE.equals(accountTypeCode)) {
                    if (realtimeGoldPrice.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal totalWeight = accountPositions.stream()
                            .map(InvestmentPositionEntity::getHoldingQuantity)
                            .filter(value -> value != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                        accountBalances.put(accountId, totalWeight.multiply(realtimeGoldPrice).setScale(2, RoundingMode.HALF_UP));
                    } else {
                        BigDecimal storedMarketValue = accountPositions.stream()
                            .map(InvestmentPositionEntity::getMarketValue)
                            .filter(value -> value != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);
                        accountBalances.put(accountId, storedMarketValue);
                    }
                    continue;
                }
                if (INVESTMENT_ACCOUNT_TYPE_CODE.equals(accountTypeCode)) {
                    BigDecimal marketValue = accountPositions.stream()
                        .map(this::resolveInvestmentPositionBalance)
                        .filter(value -> value != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    accountBalances.put(
                        accountId,
                        marketValue.add(pendingBuyAmountsByAccount.getOrDefault(accountId, BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP)
                    );
                }
            }
        }

        return new BalanceContext(accountBalances);
    }

    private <T> Map<Long, BigDecimal> sumByAccount(
        List<T> entities,
        Function<T, Long> accountIdGetter,
        Function<T, BigDecimal> amountGetter
    ) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (T entity : entities) {
            Long accountId = accountIdGetter.apply(entity);
            if (accountId == null) {
                continue;
            }
            BigDecimal amount = amountGetter.apply(entity);
            if (amount == null) {
                continue;
            }
            totals.merge(accountId, amount, BigDecimal::add);
        }
        return totals;
    }

    private BigDecimal resolvePendingBuyTransactionAmount(Long accountId) {
        if (accountId == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return investmentTransactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
                .eq(InvestmentTransactionEntity::getAccountId, accountId)
                .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
                .eq(InvestmentTransactionEntity::getSettlementStatus, SETTLEMENT_STATUS_PENDING)
                .eq(InvestmentTransactionEntity::getTradeType, BUY_TRADE_TYPE))
            .stream()
            .map(transaction -> zeroIfNull(transaction.getAmount())
                .add(zeroIfNull(transaction.getFeeAmount()))
                .add(zeroIfNull(transaction.getTaxAmount())))
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private Map<Long, BigDecimal> loadPendingBuyAmountsByAccount(List<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, BigDecimal> result = new HashMap<>();
        investmentTransactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
                .in(InvestmentTransactionEntity::getAccountId, accountIds)
                .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
                .eq(InvestmentTransactionEntity::getSettlementStatus, SETTLEMENT_STATUS_PENDING)
                .eq(InvestmentTransactionEntity::getTradeType, BUY_TRADE_TYPE))
            .forEach(transaction -> {
                Long accountId = transaction.getAccountId();
                if (accountId == null) {
                    return;
                }
                BigDecimal amount = zeroIfNull(transaction.getAmount())
                    .add(zeroIfNull(transaction.getFeeAmount()))
                    .add(zeroIfNull(transaction.getTaxAmount()))
                    .setScale(2, RoundingMode.HALF_UP);
                result.merge(accountId, amount, BigDecimal::add);
            });
        result.replaceAll((key, value) -> value.setScale(2, RoundingMode.HALF_UP));
        return result;
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BigDecimal resolveDebtBalanceDelta(DebtRecordEntity record) {
        BigDecimal amount = record.getAmount() == null ? BigDecimal.ZERO : record.getAmount().setScale(2, RoundingMode.HALF_UP);
        boolean isRepayment = DEBT_RECORD_TYPE_REPAYMENT.equalsIgnoreCase(record.getRecordType());
        if (DEBT_DIRECTION_PAYABLE.equals(record.getDirection())) {
            return isRepayment ? amount : amount.negate().setScale(2, RoundingMode.HALF_UP);
        }
        return isRepayment ? amount.negate().setScale(2, RoundingMode.HALF_UP) : amount;
    }

    private record BalanceContext(Map<Long, BigDecimal> accountBalances) {
    }

    private record LiabilityPlan(
        BigDecimal totalAmount,
        BigDecimal interestRate,
        BigDecimal interestAmount,
        Integer totalPeriods,
        Integer repaymentDay,
        LocalDate startDate
    ) {
    }
}
