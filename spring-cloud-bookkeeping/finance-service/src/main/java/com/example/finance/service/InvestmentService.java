package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.InvestmentDividendResponse;
import com.example.finance.dto.InvestmentAssetDetailResponse;
import com.example.finance.dto.InvestmentChartPointResponse;
import com.example.finance.dto.InvestmentDetailStatResponse;
import com.example.finance.dto.InvestmentPositionRequest;
import com.example.finance.dto.InvestmentPositionResponse;
import com.example.finance.dto.InvestmentProductRequest;
import com.example.finance.dto.InvestmentProductResponse;
import com.example.finance.dto.InvestmentSummaryResponse;
import com.example.finance.dto.InvestmentTransactionRequest;
import com.example.finance.dto.InvestmentTransactionResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.InvestmentDividendPlanEntity;
import com.example.finance.entity.InvestmentDividendRecordEntity;
import com.example.finance.entity.InvestmentPositionEntity;
import com.example.finance.entity.InvestmentProductEntity;
import com.example.finance.entity.InvestmentTransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.InvestmentDividendPlanMapper;
import com.example.finance.mapper.InvestmentDividendRecordMapper;
import com.example.finance.mapper.InvestmentPositionMapper;
import com.example.finance.mapper.InvestmentProductMapper;
import com.example.finance.mapper.InvestmentTransactionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InvestmentService {

    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String DEFAULT_UNIT_NAME = "份";
    private static final String ACTIVE_STATUS = "active";
    private static final String NORMAL_STATUS = "normal";
    private static final String VOIDED_STATUS = "voided";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String INVESTMENT_ACCOUNT_TYPE_CODE = "investment";
    private static final DateTimeFormatter NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final InvestmentProductMapper productMapper;
    private final InvestmentPositionMapper positionMapper;
    private final InvestmentTransactionMapper transactionMapper;
    private final InvestmentDividendPlanMapper dividendPlanMapper;
    private final InvestmentDividendRecordMapper dividendRecordMapper;
    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final HttpClient httpClient;

    public InvestmentService(
        InvestmentProductMapper productMapper,
        InvestmentPositionMapper positionMapper,
        InvestmentTransactionMapper transactionMapper,
        InvestmentDividendPlanMapper dividendPlanMapper,
        InvestmentDividendRecordMapper dividendRecordMapper,
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        ObjectMapper objectMapper
    ) {
        this.productMapper = productMapper;
        this.positionMapper = positionMapper;
        this.transactionMapper = transactionMapper;
        this.dividendPlanMapper = dividendPlanMapper;
        this.dividendRecordMapper = dividendRecordMapper;
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public List<InvestmentProductResponse> listProducts(String productType, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return fetchExternalProducts(keyword.trim(), productType);
    }

    public InvestmentProductResponse createProduct(InvestmentProductRequest request) {
        InvestmentProductEntity entity = fillProduct(new InvestmentProductEntity(), request);
        productMapper.insert(entity);
        return toProductResponse(productMapper.selectById(entity.getId()));
    }

    public List<InvestmentPositionResponse> listPositions(Long userId, Long accountId, String productType, String status) {
        LambdaQueryWrapper<InvestmentPositionEntity> wrapper = new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(userId != null, InvestmentPositionEntity::getUserId, userId)
            .eq(accountId != null, InvestmentPositionEntity::getAccountId, accountId)
            .eq(StringUtils.hasText(status), InvestmentPositionEntity::getStatus, status)
            .orderByDesc(InvestmentPositionEntity::getMarketValue)
            .orderByDesc(InvestmentPositionEntity::getId);

        List<InvestmentPositionEntity> positions = positionMapper.selectList(wrapper);
        List<InvestmentPositionResponse> responses = toPositionResponses(positions);
        if (!StringUtils.hasText(productType)) {
            return responses;
        }
        return responses.stream()
            .filter(item -> productType.equals(item.getProductType()))
            .toList();
    }

    public Optional<InvestmentPositionResponse> getPosition(Long id) {
        InvestmentPositionEntity position = positionMapper.selectById(id);
        if (position == null) {
            return Optional.empty();
        }
        return Optional.of(toPositionResponse(
            position,
            productMapper.selectById(position.getProductId()),
            accountMapper.selectById(position.getAccountId())
        ));
    }

    public Optional<InvestmentAssetDetailResponse> getPositionDetail(Long id) {
        InvestmentPositionEntity position = positionMapper.selectById(id);
        if (position == null) {
            return Optional.empty();
        }

        InvestmentProductEntity product = productMapper.selectById(position.getProductId());
        AccountEntity account = accountMapper.selectById(position.getAccountId());
        InvestmentPositionResponse positionResponse = toPositionResponse(position, product, account);
        InvestmentAssetDetailResponse response = new InvestmentAssetDetailResponse();
        response.setPosition(positionResponse);
        response.setProductType(product == null ? null : product.getProductType());
        response.setName(product == null ? null : product.getName());
        response.setSymbol(product == null ? null : product.getSymbol());
        response.setMarket(product == null ? null : product.getMarket());
        response.setUnitName(product == null ? null : product.getUnitName());
        response.setSource("本地持仓");
        response.setDescription(position.getRemark());
        response.setHoldingStats(buildHoldingStats(positionResponse));
        response.setMarketStats(new ArrayList<>());
        response.setChartPoints(Collections.emptyList());

        response.setLatestPrice(positionResponse.getCurrentPrice());
        response.setUpdatedAt(positionResponse.getLastSyncedAt() == null ? null : positionResponse.getLastSyncedAt().toString());
        response.setChartType(product != null && "stock".equals(product.getProductType()) ? "candlestick" : "line");
        response.setSource("本地持仓");
        response.setDescription("页面先展示本地持仓数据，行情和走势由前端直接从公开接口加载。");
        response.setMarketStats(List.of(
            stat("资产类型", product == null ? "-" : productTypeName(product.getProductType()), null),
            stat("资产代码", product == null ? "-" : blankToDash(product.getSymbol()), null),
            stat("市场", product == null ? "-" : blankToDash(product.getMarket()), null),
            stat("当前净值", moneyText(positionResponse.getCurrentPrice(), "stock".equals(product == null ? null : product.getProductType()) ? 2 : 4), null),
            stat("最新同步", response.getUpdatedAt() == null ? "-" : response.getUpdatedAt(), null)
        ));
        return Optional.of(response);
    }

    @Transactional
    public InvestmentPositionResponse createPosition(InvestmentPositionRequest request) {
        AccountEntity account = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        AccountEntity fundingAccount = requireCashFundingAccount(request.getUserId(), request.getFundingAccountId());
        InvestmentProductEntity product = request.getProductId() != null
            ? requireProduct(request.getProductId())
            : createOrLoadProduct(request.getProduct());

        InvestmentPositionEntity entity = new InvestmentPositionEntity();
        fillPosition(entity, request, product.getId());
        deductFundingAccount(fundingAccount, entity.getCostAmount());
        positionMapper.insert(entity);
        createInitialBuyTransaction(entity);
        syncInvestmentAccountBalance(request.getUserId(), request.getAccountId());
        return toPositionResponse(positionMapper.selectById(entity.getId()), product, account);
    }

    @Transactional
    public Optional<InvestmentPositionResponse> updatePosition(Long id, InvestmentPositionRequest request) {
        InvestmentPositionEntity entity = positionMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        Long oldAccountId = entity.getAccountId();
        AccountEntity account = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        InvestmentProductEntity product = request.getProductId() != null
            ? requireProduct(request.getProductId())
            : createOrLoadProduct(request.getProduct());
        fillPosition(entity, request, product.getId());
        positionMapper.updateById(entity);
        syncInvestmentAccountBalance(request.getUserId(), request.getAccountId());
        if (!request.getAccountId().equals(oldAccountId)) {
            syncInvestmentAccountBalance(request.getUserId(), oldAccountId);
        }
        return Optional.of(toPositionResponse(positionMapper.selectById(id), product, account));
    }

    @Transactional
    public boolean deletePosition(Long id, Long userId) {
        InvestmentPositionEntity entity = positionMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }
        boolean deleted = positionMapper.deleteById(id) > 0;
        if (deleted) {
            syncInvestmentAccountBalance(userId, entity.getAccountId());
        }
        return deleted;
    }

    public InvestmentSummaryResponse summary(Long userId, Long accountId) {
        List<InvestmentPositionEntity> positions = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(userId != null, InvestmentPositionEntity::getUserId, userId)
            .eq(accountId != null, InvestmentPositionEntity::getAccountId, accountId)
            .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS));
        BigDecimal totalMarketValue = sum(positions, InvestmentPositionEntity::getMarketValue);
        BigDecimal holdingProfit = sum(positions, InvestmentPositionEntity::getHoldingProfit);
        BigDecimal cumulativeProfit = sum(positions, InvestmentPositionEntity::getCumulativeProfit);
        BigDecimal dayProfit = sum(positions, InvestmentPositionEntity::getDayProfit);

        InvestmentSummaryResponse response = new InvestmentSummaryResponse();
        response.setUserId(userId);
        response.setTotalMarketValue(totalMarketValue);
        response.setDayProfit(dayProfit);
        response.setDayProfitRate(rate(dayProfit, totalMarketValue.subtract(dayProfit)));
        response.setHoldingProfit(holdingProfit);
        response.setHoldingProfitRate(rate(holdingProfit, totalMarketValue.subtract(holdingProfit)));
        response.setCumulativeProfit(cumulativeProfit);
        response.setCumulativeProfitRate(rate(cumulativeProfit, totalMarketValue.subtract(cumulativeProfit)));
        response.setLastSyncedAt(positions.stream()
            .map(InvestmentPositionEntity::getLastSyncedAt)
            .filter(item -> item != null)
            .max(LocalDateTime::compareTo)
            .orElse(null));
        return response;
    }

    public List<InvestmentTransactionResponse> listTransactions(Long userId, Long accountId, Long positionId) {
        List<InvestmentTransactionEntity> transactions = transactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(userId != null, InvestmentTransactionEntity::getUserId, userId)
            .eq(accountId != null, InvestmentTransactionEntity::getAccountId, accountId)
            .eq(positionId != null, InvestmentTransactionEntity::getPositionId, positionId)
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .orderByDesc(InvestmentTransactionEntity::getTradeAt)
            .orderByDesc(InvestmentTransactionEntity::getId));
        if (transactions.isEmpty() && positionId != null) {
            return inferInitialTransaction(userId, accountId, positionId);
        }
        return toTransactionResponses(transactions);
    }

    @Transactional
    public InvestmentTransactionResponse createTransaction(InvestmentTransactionRequest request) {
        requireInvestmentAccount(request.getUserId(), request.getAccountId());
        InvestmentPositionEntity position = requirePosition(request);
        InvestmentProductEntity product = requireProduct(request.getProductId());
        BigDecimal quantity = defaultZero(request.getQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal price = defaultZero(request.getPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal feeAmount = defaultZero(request.getFeeAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = defaultZero(request.getTaxAmount()).setScale(2, RoundingMode.HALF_UP);

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易数量必须大于0");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易金额必须大于0");
        }

        if ("buy".equals(request.getTradeType())) {
            AccountEntity fundingAccount = requireCashFundingAccount(request.getUserId(), request.getFundingAccountId());
            deductFundingAccount(fundingAccount, amount.add(feeAmount).add(taxAmount));
            applyBuyTransaction(position, quantity, price, amount, feeAmount, taxAmount);
        } else if ("sell".equals(request.getTradeType())) {
            AccountEntity fundingAccount = requireCashFundingAccount(request.getUserId(), request.getFundingAccountId());
            creditFundingAccount(fundingAccount, amount.subtract(feeAmount).subtract(taxAmount));
            applySellTransaction(position, quantity, price, amount, feeAmount, taxAmount);
        }

        InvestmentTransactionEntity entity = new InvestmentTransactionEntity();
        entity.setTransactionNo(generateTransactionNo());
        entity.setUserId(request.getUserId());
        entity.setAccountId(request.getAccountId());
        entity.setPositionId(request.getPositionId());
        entity.setProductId(request.getProductId());
        entity.setTradeType(request.getTradeType());
        entity.setQuantity(quantity);
        entity.setPrice(price);
        entity.setAmount(amount);
        entity.setFeeAmount(feeAmount);
        entity.setTaxAmount(taxAmount);
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setTradeAt(request.getTradeAt());
        entity.setStatus(NORMAL_STATUS);
        entity.setRemark(request.getRemark());
        transactionMapper.insert(entity);
        positionMapper.updateById(position);
        syncInvestmentAccountBalance(request.getUserId(), request.getAccountId());
        return toTransactionResponse(entity, product, accountMapper.selectById(entity.getAccountId()));
    }

    public boolean deleteTransaction(Long id, Long userId) {
        InvestmentTransactionEntity entity = transactionMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }
        entity.setStatus(VOIDED_STATUS);
        transactionMapper.updateById(entity);
        return true;
    }

    public List<InvestmentDividendResponse> listDividends(Long userId, Long accountId) {
        List<InvestmentDividendRecordEntity> records = dividendRecordMapper.selectList(new LambdaQueryWrapper<InvestmentDividendRecordEntity>()
            .eq(userId != null, InvestmentDividendRecordEntity::getUserId, userId)
            .eq(accountId != null, InvestmentDividendRecordEntity::getAccountId, accountId)
            .eq(InvestmentDividendRecordEntity::getStatus, NORMAL_STATUS)
            .orderByDesc(InvestmentDividendRecordEntity::getPaidAt));
        List<InvestmentDividendResponse> actual = records.stream().map(this::toDividendRecordResponse).toList();
        if (!actual.isEmpty()) {
            return actual;
        }

        List<Long> productIds = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .eq(userId != null, InvestmentPositionEntity::getUserId, userId)
                .eq(accountId != null, InvestmentPositionEntity::getAccountId, accountId)
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS))
            .stream()
            .map(InvestmentPositionEntity::getProductId)
            .distinct()
            .toList();
        if (productIds.isEmpty()) {
            return Collections.emptyList();
        }
        return dividendPlanMapper.selectList(new LambdaQueryWrapper<InvestmentDividendPlanEntity>()
                .in(InvestmentDividendPlanEntity::getProductId, productIds)
                .orderByDesc(InvestmentDividendPlanEntity::getPayDate))
            .stream()
            .map(this::toDividendPlanResponse)
            .toList();
    }

    private InvestmentProductEntity createOrLoadProduct(InvestmentProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("投资产品不能为空");
        }
        InvestmentProductEntity exists = productMapper.selectOne(new LambdaQueryWrapper<InvestmentProductEntity>()
            .eq(InvestmentProductEntity::getProductType, request.getProductType())
            .eq(InvestmentProductEntity::getSymbol, request.getSymbol())
            .eq(StringUtils.hasText(request.getMarket()), InvestmentProductEntity::getMarket, request.getMarket())
            .last("LIMIT 1"));
        if (exists != null) {
            return exists;
        }
        InvestmentProductEntity entity = fillProduct(new InvestmentProductEntity(), request);
        productMapper.insert(entity);
        return productMapper.selectById(entity.getId());
    }

    private InvestmentProductEntity fillProduct(InvestmentProductEntity entity, InvestmentProductRequest request) {
        entity.setProductType(request.getProductType());
        entity.setMarket(request.getMarket());
        entity.setExchangeCode(request.getExchangeCode());
        entity.setSymbol(request.getSymbol());
        entity.setName(request.getName());
        entity.setShortName(request.getShortName());
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setUnitName(StringUtils.hasText(request.getUnitName()) ? request.getUnitName() : DEFAULT_UNIT_NAME);
        entity.setPricePrecision(request.getPricePrecision() != null ? request.getPricePrecision() : 4);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : ACTIVE_STATUS);
        entity.setRemark(request.getRemark());
        return entity;
    }

    private void fillPosition(InvestmentPositionEntity entity, InvestmentPositionRequest request, Long productId) {
        BigDecimal quantity = defaultZero(request.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal costAmount = defaultZero(request.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPrice = defaultZero(request.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal marketValue = quantity.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = quantity.compareTo(BigDecimal.ZERO) > 0
            ? costAmount.divide(quantity, 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        BigDecimal holdingProfit = marketValue.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);

        entity.setUserId(request.getUserId());
        entity.setAccountId(request.getAccountId());
        entity.setProductId(productId);
        entity.setHoldingQuantity(quantity);
        entity.setAvailableQuantity(defaultZero(request.getAvailableQuantity() == null ? quantity : request.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP));
        entity.setFrozenQuantity(defaultZero(request.getFrozenQuantity()).setScale(6, RoundingMode.HALF_UP));
        entity.setCostAmount(costAmount);
        entity.setAvgCostPrice(avgCostPrice);
        entity.setCurrentPrice(currentPrice);
        entity.setMarketValue(marketValue);
        entity.setHoldingProfit(holdingProfit);
        entity.setHoldingProfitRate(rate(holdingProfit, costAmount));
        entity.setCumulativeProfit(holdingProfit);
        entity.setCumulativeProfitRate(rate(holdingProfit, costAmount));
        entity.setDayProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setDayProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        entity.setIncludeInNetWorth(request.getIncludeInNetWorth() == null ? Boolean.TRUE : request.getIncludeInNetWorth());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : ACTIVE_STATUS);
        entity.setLastSyncedAt(LocalDateTime.now());
        entity.setRemark(request.getRemark());
    }

    private void createInitialBuyTransaction(InvestmentPositionEntity position) {
        InvestmentTransactionEntity transaction = new InvestmentTransactionEntity();
        transaction.setTransactionNo(generateTransactionNo());
        transaction.setUserId(position.getUserId());
        transaction.setAccountId(position.getAccountId());
        transaction.setPositionId(position.getId());
        transaction.setProductId(position.getProductId());
        transaction.setTradeType("buy");
        transaction.setQuantity(position.getHoldingQuantity());
        transaction.setPrice(position.getAvgCostPrice());
        transaction.setAmount(position.getCostAmount());
        transaction.setFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        transaction.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        transaction.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        transaction.setTradeAt(LocalDateTime.now());
        transaction.setStatus(NORMAL_STATUS);
        transaction.setRemark("新增资产买入");
        transactionMapper.insert(transaction);
    }

    private AccountEntity requireInvestmentAccount(Long userId, Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("投资账户不存在");
        }
        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !INVESTMENT_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("请选择投资账户");
        }
        return account;
    }

    private AccountEntity requireCashFundingAccount(Long userId, Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("请选择资金账户");
        }
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("资金账户不存在");
        }
        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !CASH_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("资金账户必须为现金账户");
        }
        return account;
    }

    private void deductFundingAccount(AccountEntity account, BigDecimal amount) {
        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        BigDecimal nextBalance = currentBalance.subtract(defaultZero(amount).setScale(2, RoundingMode.HALF_UP));
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("资金账户余额不足");
        }
        account.setCurrentBalance(nextBalance);
        accountMapper.updateById(account);
    }

    private void creditFundingAccount(AccountEntity account, BigDecimal amount) {
        BigDecimal netAmount = defaultZero(amount).setScale(2, RoundingMode.HALF_UP);
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("到账金额不能小于0");
        }
        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        account.setCurrentBalance(currentBalance.add(netAmount).setScale(2, RoundingMode.HALF_UP));
        accountMapper.updateById(account);
    }

    private InvestmentPositionEntity requirePosition(InvestmentTransactionRequest request) {
        if (request.getPositionId() == null) {
            throw new IllegalArgumentException("请选择投资持仓");
        }
        InvestmentPositionEntity position = positionMapper.selectById(request.getPositionId());
        if (position == null || !request.getUserId().equals(position.getUserId())) {
            throw new IllegalArgumentException("投资持仓不存在");
        }
        if (!request.getAccountId().equals(position.getAccountId())) {
            throw new IllegalArgumentException("投资账户不匹配");
        }
        if (!request.getProductId().equals(position.getProductId())) {
            throw new IllegalArgumentException("投资产品不匹配");
        }
        return position;
    }

    private void applyBuyTransaction(
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal taxAmount
    ) {
        BigDecimal nextHoldingQuantity = defaultZero(position.getHoldingQuantity()).add(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextAvailableQuantity = defaultZero(position.getAvailableQuantity()).add(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextCostAmount = defaultZero(position.getCostAmount()).add(amount).add(feeAmount).add(taxAmount).setScale(2, RoundingMode.HALF_UP);
        position.setHoldingQuantity(nextHoldingQuantity);
        position.setAvailableQuantity(nextAvailableQuantity);
        position.setCostAmount(nextCostAmount);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            position.setCurrentPrice(price);
        }
        recalculatePositionMetrics(position, ACTIVE_STATUS);
    }

    private void applySellTransaction(
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal taxAmount
    ) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal availableQuantity = defaultZero(position.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (quantity.compareTo(holdingQuantity) > 0 || quantity.compareTo(availableQuantity) > 0) {
            throw new IllegalArgumentException("卖出数量不能超过当前持仓");
        }

        BigDecimal nextHoldingQuantity = holdingQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextAvailableQuantity = availableQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = defaultZero(position.getAvgCostPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal soldCostAmount = avgCostPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nextCostAmount = defaultZero(position.getCostAmount()).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        if (nextCostAmount.compareTo(BigDecimal.ZERO) < 0) {
            nextCostAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        position.setHoldingQuantity(nextHoldingQuantity);
        position.setAvailableQuantity(nextAvailableQuantity);
        position.setCostAmount(nextCostAmount);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            position.setCurrentPrice(price);
        }
        recalculatePositionMetrics(position, nextHoldingQuantity.compareTo(BigDecimal.ZERO) == 0 ? "closed" : ACTIVE_STATUS);

        BigDecimal realizedProfit = amount.subtract(feeAmount).subtract(taxAmount).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        position.setCumulativeProfit(defaultZero(position.getHoldingProfit()).add(realizedProfit).setScale(2, RoundingMode.HALF_UP));
        position.setCumulativeProfitRate(rate(position.getCumulativeProfit(), position.getMarketValue().add(soldCostAmount)));
    }

    private void recalculatePositionMetrics(InvestmentPositionEntity position, String status) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal costAmount = defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPrice = defaultZero(position.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal marketValue = holdingQuantity.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = holdingQuantity.compareTo(BigDecimal.ZERO) > 0
            ? costAmount.divide(holdingQuantity, 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        BigDecimal holdingProfit = marketValue.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);

        position.setHoldingQuantity(holdingQuantity);
        position.setAvailableQuantity(defaultZero(position.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP));
        position.setCostAmount(costAmount);
        position.setAvgCostPrice(avgCostPrice);
        position.setMarketValue(marketValue);
        position.setHoldingProfit(holdingProfit);
        position.setHoldingProfitRate(rate(holdingProfit, costAmount));
        position.setCumulativeProfit(holdingProfit);
        position.setCumulativeProfitRate(rate(holdingProfit, costAmount));
        position.setStatus(status);
        position.setLastSyncedAt(LocalDateTime.now());
    }

    private void syncInvestmentAccountBalance(Long userId, Long accountId) {
        if (accountId == null) {
            return;
        }
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId())) {
            return;
        }
        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !INVESTMENT_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            return;
        }
        BigDecimal marketValue = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .eq(InvestmentPositionEntity::getUserId, userId)
                .eq(InvestmentPositionEntity::getAccountId, accountId)
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS))
            .stream()
            .map(InvestmentPositionEntity::getMarketValue)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        account.setCurrentBalance(marketValue);
        accountMapper.updateById(account);
    }

    private InvestmentProductEntity requireProduct(Long productId) {
        InvestmentProductEntity product = productMapper.selectById(productId);
        if (product == null) {
            throw new IllegalArgumentException("投资产品不存在");
        }
        return product;
    }

    private List<InvestmentPositionResponse> toPositionResponses(List<InvestmentPositionEntity> positions) {
        if (positions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, InvestmentProductEntity> products = productMapper.selectBatchIds(positions.stream().map(InvestmentPositionEntity::getProductId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        Map<Long, AccountEntity> accounts = accountMapper.selectBatchIds(positions.stream().map(InvestmentPositionEntity::getAccountId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        return positions.stream().map(item -> toPositionResponse(item, products.get(item.getProductId()), accounts.get(item.getAccountId()))).toList();
    }

    private InvestmentPositionResponse toPositionResponse(InvestmentPositionEntity entity, InvestmentProductEntity product, AccountEntity account) {
        InvestmentPositionResponse response = new InvestmentPositionResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setAccountId(entity.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setProductId(entity.getProductId());
        if (product != null) {
            response.setProductType(product.getProductType());
            response.setProductName(product.getName());
            response.setProductSymbol(product.getSymbol());
            response.setMarket(product.getMarket());
            response.setUnitName(product.getUnitName());
            response.setCurrencyCode(product.getCurrencyCode());
        }
        response.setHoldingQuantity(entity.getHoldingQuantity());
        response.setAvailableQuantity(entity.getAvailableQuantity());
        response.setFrozenQuantity(entity.getFrozenQuantity());
        response.setCostAmount(entity.getCostAmount());
        response.setAvgCostPrice(entity.getAvgCostPrice());
        response.setCurrentPrice(entity.getCurrentPrice());
        response.setMarketValue(entity.getMarketValue());
        response.setDayProfit(entity.getDayProfit());
        response.setDayProfitRate(entity.getDayProfitRate());
        response.setHoldingProfit(entity.getHoldingProfit());
        response.setHoldingProfitRate(entity.getHoldingProfitRate());
        response.setCumulativeProfit(entity.getCumulativeProfit());
        response.setCumulativeProfitRate(entity.getCumulativeProfitRate());
        response.setIncludeInNetWorth(entity.getIncludeInNetWorth());
        response.setStatus(entity.getStatus());
        response.setLastSyncedAt(entity.getLastSyncedAt());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private List<InvestmentTransactionResponse> toTransactionResponses(List<InvestmentTransactionEntity> transactions) {
        if (transactions.isEmpty()) return Collections.emptyList();
        Set<Long> productIds = transactions.stream().map(InvestmentTransactionEntity::getProductId).collect(Collectors.toSet());
        Set<Long> accountIds = transactions.stream().map(InvestmentTransactionEntity::getAccountId).collect(Collectors.toSet());
        Map<Long, InvestmentProductEntity> products = productMapper.selectBatchIds(productIds).stream().collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        Map<Long, AccountEntity> accounts = accountMapper.selectBatchIds(accountIds).stream().collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        return transactions.stream().map(item -> toTransactionResponse(item, products.get(item.getProductId()), accounts.get(item.getAccountId()))).toList();
    }

    private List<InvestmentTransactionResponse> inferInitialTransaction(Long userId, Long accountId, Long positionId) {
        InvestmentPositionEntity position = positionMapper.selectById(positionId);
        if (position == null || (userId != null && !userId.equals(position.getUserId())) || (accountId != null && !accountId.equals(position.getAccountId()))) {
            return Collections.emptyList();
        }
        InvestmentTransactionResponse response = new InvestmentTransactionResponse();
        response.setId(-position.getId());
        response.setTransactionNo("INIT-" + position.getId());
        response.setUserId(position.getUserId());
        response.setAccountId(position.getAccountId());
        AccountEntity account = accountMapper.selectById(position.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setPositionId(position.getId());
        response.setProductId(position.getProductId());
        InvestmentProductEntity product = productMapper.selectById(position.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setTradeType("buy");
        response.setQuantity(position.getHoldingQuantity());
        response.setPrice(position.getAvgCostPrice());
        response.setAmount(position.getCostAmount());
        response.setFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        response.setTradeAt(position.getCreatedAt());
        response.setStatus(NORMAL_STATUS);
        response.setRemark("初始买入");
        response.setCreatedAt(position.getCreatedAt());
        response.setUpdatedAt(position.getUpdatedAt());
        return List.of(response);
    }

    private InvestmentTransactionResponse toTransactionResponse(InvestmentTransactionEntity entity, InvestmentProductEntity product, AccountEntity account) {
        InvestmentTransactionResponse response = new InvestmentTransactionResponse();
        response.setId(entity.getId());
        response.setTransactionNo(entity.getTransactionNo());
        response.setUserId(entity.getUserId());
        response.setAccountId(entity.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setPositionId(entity.getPositionId());
        response.setProductId(entity.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setTradeType(entity.getTradeType());
        response.setQuantity(entity.getQuantity());
        response.setPrice(entity.getPrice());
        response.setAmount(entity.getAmount());
        response.setFeeAmount(entity.getFeeAmount());
        response.setTaxAmount(entity.getTaxAmount());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setTradeAt(entity.getTradeAt());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private InvestmentProductResponse toProductResponse(InvestmentProductEntity entity) {
        InvestmentProductResponse response = new InvestmentProductResponse();
        response.setId(entity.getId());
        response.setProductType(entity.getProductType());
        response.setMarket(entity.getMarket());
        response.setExchangeCode(entity.getExchangeCode());
        response.setSymbol(entity.getSymbol());
        response.setName(entity.getName());
        response.setShortName(entity.getShortName());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setUnitName(entity.getUnitName());
        response.setPricePrecision(entity.getPricePrecision());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private List<InvestmentDetailStatResponse> buildHoldingStats(InvestmentPositionResponse position) {
        return List.of(
            stat("所属账户", blankToDash(position.getAccountName()), null),
            stat("持仓数量", moneyText(position.getHoldingQuantity(), 2) + " " + blankToDefault(position.getUnitName(), DEFAULT_UNIT_NAME), null),
            stat("持仓成本", currencyText(position.getCostAmount(), 2), null),
            stat("当前市值", currencyText(position.getMarketValue(), 2), null),
            stat("持仓收益", currencyText(position.getHoldingProfit(), 2), tone(position.getHoldingProfit())),
            stat("收益率", percentText(position.getHoldingProfitRate()), tone(position.getHoldingProfitRate()))
        );
    }

    private void fillFundDetail(InvestmentAssetDetailResponse response, InvestmentProductEntity product) {
        JsonNode baseInfo = fetchFundBaseInfo(product.getSymbol()).path("Datas");
        JsonNode estimateInfo = fetchFundEstimateInfo(product.getSymbol());
        BigDecimal estimatePrice = safeDecimal(estimateInfo.path("gsz").asText(null));
        BigDecimal officialPrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
        BigDecimal latestPrice = estimatePrice != null ? estimatePrice : officialPrice;
        BigDecimal changePercent = estimatePrice != null
            ? safeDecimal(estimateInfo.path("gszzl").asText(null))
            : safeDecimal(baseInfo.path("RZDF").asText(null));
        String updatedAt = estimatePrice != null
            ? estimateInfo.path("gztime").asText(null)
            : baseInfo.path("FSRQ").asText(null);

        response.setProductType("fund");
        response.setName(blankToDefault(baseInfo.path("SHORTNAME").asText(null), product.getName()));
        response.setSymbol(blankToDefault(baseInfo.path("FCODE").asText(null), product.getSymbol()));
        response.setLatestPrice(latestPrice);
        response.setChangePercent(changePercent);
        response.setUpdatedAt(updatedAt);
        response.setChartType("line");
        response.setSource("东方财富");
        response.setDescription("基金详情、估算净值和近一年走势来自东方财富公开接口。");
        response.setMarketStats(List.of(
            stat("资产类型", "基金", null),
            stat("基金代码", product.getSymbol(), null),
            stat("基金类型", blankToDash(baseInfo.path("FTYPE").asText(null)), null),
            stat(estimatePrice != null ? "当前净值（估算）" : "当前净值（单位净值）", latestPrice == null ? "-" : moneyText(latestPrice, 4), tone(changePercent)),
            stat("累计净值", blankToDash(baseInfo.path("LJJZ").asText(null)), null),
            stat("当日涨跌幅", percentText(changePercent), tone(changePercent)),
            stat(estimatePrice != null ? "估值时间" : "净值日期", blankToDash(updatedAt), null),
            stat("最新官方净值", blankToDash(blankToDefault(estimateInfo.path("dwjz").asText(null), baseInfo.path("DWJZ").asText(null))), null),
            stat("基金公司", blankToDash(baseInfo.path("JJGS").asText(null)), null),
            stat("申购状态", blankToDash(baseInfo.path("SGZT").asText(null)), null),
            stat("赎回状态", blankToDash(baseInfo.path("SHZT").asText(null)), null)
        ));
        response.setChartPoints(fetchFundTrendPoints(product.getSymbol()));
    }

    private void fillStockDetail(InvestmentAssetDetailResponse response, InvestmentProductEntity product) {
        String symbol = toTencentSymbol(product.getSymbol(), product.getExchangeCode());
        JsonNode quote = fetchTencentQuoteFields(symbol);
        BigDecimal latestPrice = safeDecimal(quote.path("price").asText(null));
        BigDecimal change = safeDecimal(quote.path("change").asText(null));
        BigDecimal changePercent = safeDecimal(quote.path("changePercent").asText(null));

        response.setProductType("stock");
        response.setName(blankToDefault(quote.path("name").asText(null), product.getName()));
        response.setSymbol(blankToDefault(quote.path("code").asText(null), product.getSymbol()));
        response.setLatestPrice(latestPrice);
        response.setChange(change);
        response.setChangePercent(changePercent);
        response.setUpdatedAt(formatTencentTime(quote.path("timeRaw").asText(null)));
        response.setChartType("candlestick");
        response.setSource("腾讯行情");
        response.setDescription("股票实时行情和日 K 走势来自腾讯公开行情接口。");
        response.setMarketStats(List.of(
            stat("资产类型", "股票", null),
            stat("股票代码", product.getSymbol(), null),
            stat("市场", blankToDash(product.getExchangeCode()), null),
            stat("当前净值（当前价）", latestPrice == null ? "-" : moneyText(latestPrice, 2), tone(change)),
            stat("涨跌额", moneyText(change, 2), tone(change)),
            stat("涨跌幅", percentText(changePercent), tone(changePercent)),
            stat("今开", moneyText(safeDecimal(quote.path("open").asText(null)), 2), null),
            stat("昨收", moneyText(safeDecimal(quote.path("prevClose").asText(null)), 2), null),
            stat("最高", moneyText(safeDecimal(quote.path("high").asText(null)), 2), null),
            stat("最低", moneyText(safeDecimal(quote.path("low").asText(null)), 2), null),
            stat("成交量（手）", blankToDash(quote.path("volume").asText(null)), null),
            stat("换手率", percentText(safeDecimal(quote.path("turnoverRate").asText(null))), null),
            stat("市盈率", blankToDash(quote.path("pe").asText(null)), null),
            stat("更新时间", blankToDash(response.getUpdatedAt()), null)
        ));
        response.setChartPoints(fetchStockKlinePoints(symbol));
    }

    private JsonNode fetchFundBaseInfo(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://fundmobapi.eastmoney.com/FundMApi/FundBaseTypeInformation.ashx?FCODE=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                        + "&deviceid=Wap&plat=Wap&product=EFund&version=2.0.0"
                ))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return objectMapper.readTree(new String(httpResponse.body(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode fetchFundEstimateInfo(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://fund.eastmoney.com/data/funddataforgznew.aspx?fc=" + URLEncoder.encode(code, StandardCharsets.UTF_8) + "&t=basewap"
                ))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return objectMapper.readTree(extractJsonpObject(new String(httpResponse.body(), StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private List<InvestmentChartPointResponse> fetchFundTrendPoints(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://fund.eastmoney.com/pingzhongdata/" + URLEncoder.encode(code, StandardCharsets.UTF_8) + ".js?v=" + System.currentTimeMillis()
                ))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String body = new String(httpResponse.body(), StandardCharsets.UTF_8);
            JsonNode rows = objectMapper.readTree(extractJsArray(body, "Data_netWorthTrend"));
            if (!rows.isArray()) {
                return Collections.emptyList();
            }
            List<InvestmentChartPointResponse> points = new ArrayList<>();
            long cutoff = System.currentTimeMillis() - 365L * 24L * 60L * 60L * 1000L;
            for (JsonNode row : rows) {
                long timestamp = row.path("x").asLong(0);
                if (timestamp < cutoff) {
                    continue;
                }
                BigDecimal value = safeDecimal(row.path("y").asText(null));
                if (value == null) {
                    continue;
                }
                InvestmentChartPointResponse point = new InvestmentChartPointResponse();
                point.setLabel(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString());
                point.setValue(value);
                points.add(point);
            }
            return points;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private JsonNode fetchTencentQuoteFields(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://qt.gtimg.cn/q=" + symbol))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String body = new String(httpResponse.body(), Charset.forName("GBK"));
            int start = body.indexOf('"');
            int end = body.lastIndexOf('"');
            if (start < 0 || end <= start) {
                return objectMapper.createObjectNode();
            }
            String[] fields = body.substring(start + 1, end).split("~");
            com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
            node.put("name", field(fields, 1));
            node.put("code", field(fields, 2));
            node.put("price", field(fields, 3));
            node.put("prevClose", field(fields, 4));
            node.put("open", field(fields, 5));
            node.put("volume", field(fields, 6));
            node.put("timeRaw", field(fields, 30));
            node.put("change", field(fields, 31));
            node.put("changePercent", field(fields, 32));
            node.put("high", field(fields, 33));
            node.put("low", field(fields, 34));
            node.put("turnoverRate", field(fields, 38));
            node.put("pe", field(fields, 39));
            return node;
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private List<InvestmentChartPointResponse> fetchStockKlinePoints(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=" + URLEncoder.encode(symbol + ",day,,,260,qfq", StandardCharsets.UTF_8)
                ))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            JsonNode root = objectMapper.readTree(new String(httpResponse.body(), StandardCharsets.UTF_8));
            JsonNode rows = root.path("data").path(symbol).path("qfqday");
            if (!rows.isArray() || rows.isEmpty()) {
                rows = root.path("data").path(symbol).path("day");
            }
            if (!rows.isArray()) {
                return Collections.emptyList();
            }
            List<InvestmentChartPointResponse> points = new ArrayList<>();
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 6) {
                    continue;
                }
                InvestmentChartPointResponse point = new InvestmentChartPointResponse();
                point.setLabel(row.get(0).asText());
                point.setOpen(safeDecimal(row.get(1).asText(null)));
                point.setClose(safeDecimal(row.get(2).asText(null)));
                point.setHigh(safeDecimal(row.get(3).asText(null)));
                point.setLow(safeDecimal(row.get(4).asText(null)));
                point.setVolume(safeDecimal(row.get(5).asText(null)));
                point.setValue(point.getClose());
                points.add(point);
            }
            return points;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private InvestmentDetailStatResponse stat(String label, String value, String tone) {
        return new InvestmentDetailStatResponse(label, value, tone);
    }

    private List<InvestmentProductResponse> fetchExternalProducts(String keyword, String productType) {
        if (keyword.matches("\\d{6}") && (!StringUtils.hasText(productType) || "fund".equals(productType))) {
            Optional<InvestmentProductResponse> fund = fetchFundProduct(keyword);
            if (fund.isPresent()) {
                return List.of(fund.get());
            }
        }

        if (keyword.matches("\\d{6}") && (!StringUtils.hasText(productType) || "stock".equals(productType))) {
            Optional<InvestmentProductResponse> stock = fetchStockProduct(keyword);
            if (stock.isPresent()) {
                return List.of(stock.get());
            }
        }

        return fetchEastMoneyProducts(keyword, productType);
    }

    private Optional<InvestmentProductResponse> fetchFundProduct(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://fundgz.1234567.com.cn/js/" + code + ".js"))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/javascript,text/javascript,*/*")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                return Optional.empty();
            }

            String body = new String(httpResponse.body(), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(extractJsonpObject(body));
            String name = node.path("name").asText("");
            if (!StringUtils.hasText(name)) {
                return Optional.empty();
            }

            InvestmentProductResponse response = new InvestmentProductResponse();
            response.setProductType("fund");
            response.setMarket("FUND");
            response.setSymbol(node.path("fundcode").asText(code));
            response.setName(name);
            response.setShortName(name);
            response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
            response.setUnitName(DEFAULT_UNIT_NAME);
            response.setPricePrecision(4);
            response.setLatestPrice(decimalText(node.path("gsz").asText(null), node.path("dwjz").asText(null)));
            response.setStatus(ACTIVE_STATUS);
            return Optional.of(response);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private List<InvestmentProductResponse> fetchEastMoneyProducts(String keyword, String productType) {
        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://searchapi.eastmoney.com/api/suggest/get?input=" + encodedKeyword + "&type=14&count=20&cb=searchResult"
                ))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                return Collections.emptyList();
            }

            String body = new String(httpResponse.body(), StandardCharsets.UTF_8);
            JsonNode rows = objectMapper.readTree(extractJsonpObject(body)).path("QuotationCodeTable").path("Data");
            if (!rows.isArray()) {
                return Collections.emptyList();
            }

            return java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .map(row -> toExternalProductFromSuggestion(row, productType))
                .flatMap(Optional::stream)
                .limit(8)
                .toList();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private Optional<InvestmentProductResponse> toExternalProductFromSuggestion(JsonNode row, String productType) {
        String code = row.path("Code").asText("");
        String classify = row.path("Classify").asText("");
        String securityTypeName = row.path("SecurityTypeName").asText("");
        String resolvedType = resolveProductType(classify, securityTypeName);
        if (!StringUtils.hasText(code) || !StringUtils.hasText(resolvedType)) {
            return Optional.empty();
        }
        if (StringUtils.hasText(productType) && !productType.equals(resolvedType)) {
            return Optional.empty();
        }

        if ("fund".equals(resolvedType)) {
            return fetchFundProduct(code).or(() -> Optional.of(toBasicExternalProduct(row, "fund", "FUND", null, "份", 4)));
        }
        if ("stock".equals(resolvedType)) {
            return fetchStockProduct(code)
                .or(() -> fetchTencentStockProduct(code))
                .or(() -> Optional.of(toBasicExternalProduct(row, "stock", "CN", resolveExchangeCode(row), "股", 2)));
        }
        if ("bond".equals(resolvedType)) {
            return Optional.of(toBasicExternalProduct(row, "bond", "CN", resolveExchangeCode(row), "张", 4));
        }
        return fetchTencentStockProduct(code);
    }

    private Optional<InvestmentProductResponse> fetchTencentStockProduct(String code) {
        for (String symbol : tencentStockSymbols(code)) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://qt.gtimg.cn/q=" + symbol))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
                HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                    continue;
                }

                String body = new String(httpResponse.body(), Charset.forName("GBK"));
                int start = body.indexOf('"');
                int end = body.lastIndexOf('"');
                if (start < 0 || end <= start) {
                    continue;
                }
                String[] fields = body.substring(start + 1, end).split("~");
                if (fields.length < 4 || !StringUtils.hasText(fields[1])) {
                    continue;
                }

                InvestmentProductResponse response = new InvestmentProductResponse();
                response.setProductType("stock");
                response.setMarket("CN");
                response.setExchangeCode(symbol.startsWith("sh") ? "SSE" : "SZSE");
                response.setSymbol(fields[2]);
                response.setName(fields[1]);
                response.setShortName(fields[1]);
                response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
                response.setUnitName("股");
                response.setPricePrecision(2);
                response.setLatestPrice(decimalText(fields[3], null));
                response.setStatus(ACTIVE_STATUS);
                return Optional.of(response);
            } catch (Exception ex) {
                // Try next market prefix.
            }
        }
        return Optional.empty();
    }

    private String resolveProductType(String classify, String securityTypeName) {
        if ("OTCFUND".equals(classify) || securityTypeName.contains("基金")) {
            return "fund";
        }
        if ("AStock".equals(classify) || securityTypeName.contains("A")) {
            return "stock";
        }
        if ("Bond".equals(classify) || securityTypeName.contains("债券")) {
            return "bond";
        }
        return null;
    }

    private InvestmentProductResponse toBasicExternalProduct(JsonNode row, String productType, String market, String exchangeCode, String unitName, int pricePrecision) {
        InvestmentProductResponse response = new InvestmentProductResponse();
        response.setProductType(productType);
        response.setMarket(market);
        response.setExchangeCode(exchangeCode);
        response.setSymbol(row.path("Code").asText(""));
        response.setName(row.path("Name").asText(""));
        response.setShortName(row.path("Name").asText(""));
        response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        response.setUnitName(unitName);
        response.setPricePrecision(pricePrecision);
        response.setStatus(ACTIVE_STATUS);
        return response;
    }

    private String resolveExchangeCode(JsonNode row) {
        String quoteId = row.path("QuoteID").asText("");
        if (quoteId.startsWith("1.")) {
            return "SSE";
        }
        if (quoteId.startsWith("0.")) {
            return "SZSE";
        }
        return null;
    }

    private Optional<InvestmentProductResponse> fetchStockProduct(String code) {
        for (String secid : stockSecids(code)) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://push2.eastmoney.com/api/qt/stock/get?secid=" + secid + "&fields=f57,f58,f43"))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept-Encoding", "identity")
                    .GET()
                    .build();
                HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                    continue;
                }

                String body = new String(httpResponse.body(), StandardCharsets.UTF_8);
                JsonNode data = objectMapper.readTree(body).path("data");
                String name = data.path("f58").asText("");
                if (!StringUtils.hasText(name)) {
                    continue;
                }

                InvestmentProductResponse response = new InvestmentProductResponse();
                response.setProductType("stock");
                response.setMarket("CN");
                response.setExchangeCode(secid.startsWith("1.") ? "SSE" : "SZSE");
                response.setSymbol(data.path("f57").asText(code));
                response.setName(name);
                response.setShortName(name);
                response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
                response.setUnitName("股");
                response.setPricePrecision(2);
                BigDecimal rawPrice = decimalText(data.path("f43").asText(null), null);
                response.setLatestPrice(rawPrice == null ? null : rawPrice.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                response.setStatus(ACTIVE_STATUS);
                return Optional.of(response);
            } catch (Exception ex) {
                // Try next market prefix.
            }
        }
        return Optional.empty();
    }

    private List<String> stockSecids(String code) {
        if (code.startsWith("6")) {
            return List.of("1." + code, "0." + code);
        }
        return List.of("0." + code, "1." + code);
    }

    private List<String> tencentStockSymbols(String code) {
        if (code.startsWith("6")) {
            return List.of("sh" + code, "sz" + code);
        }
        return List.of("sz" + code, "sh" + code);
    }

    private String toTencentSymbol(String code, String exchangeCode) {
        if ("SSE".equals(exchangeCode)) {
            return "sh" + code;
        }
        if ("SZSE".equals(exchangeCode)) {
            return "sz" + code;
        }
        return code.startsWith("6") ? "sh" + code : "sz" + code;
    }

    private String extractJsonpObject(String body) {
        int start = body == null ? -1 : body.indexOf('{');
        int end = body == null ? -1 : body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("外部产品数据格式错误");
        }
        return body.substring(start, end + 1);
    }

    private String extractJsArray(String body, String variableName) {
        String marker = "var " + variableName + " = ";
        int start = body == null ? -1 : body.indexOf(marker);
        if (start < 0) {
            marker = variableName + " = ";
            start = body == null ? -1 : body.indexOf(marker);
        }
        if (start < 0) {
            throw new IllegalArgumentException("外部走势数据格式错误");
        }
        int arrayStart = body.indexOf('[', start + marker.length());
        int semicolon = body.indexOf(';', arrayStart);
        if (arrayStart < 0 || semicolon <= arrayStart) {
            throw new IllegalArgumentException("外部走势数据格式错误");
        }
        return body.substring(arrayStart, semicolon);
    }

    private BigDecimal decimalText(String first, String second) {
        String value = StringUtils.hasText(first) && !"--".equals(first) ? first : second;
        if (!StringUtils.hasText(value) || "--".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
    }

    private BigDecimal safeDecimal(String value) {
        if (!StringUtils.hasText(value) || "--".equals(value) || "-".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private String field(String[] fields, int index) {
        return fields.length > index ? fields[index] : "";
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String blankToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String moneyText(BigDecimal value, int scale) {
        if (value == null) {
            return "-";
        }
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String currencyText(BigDecimal value, int scale) {
        String text = moneyText(value == null ? null : value.abs(), scale);
        if ("-".equals(text)) {
            return "-";
        }
        return (value.compareTo(BigDecimal.ZERO) < 0 ? "-¥" : "¥") + text;
    }

    private String percentText(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private String tone(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "neutral";
        }
        return value.compareTo(BigDecimal.ZERO) > 0 ? "positive" : "negative";
    }

    private String productTypeName(String productType) {
        if ("stock".equals(productType)) return "股票";
        if ("fund".equals(productType)) return "基金";
        if ("bond".equals(productType)) return "债券";
        if ("gold".equals(productType)) return "黄金";
        return "其他";
    }

    private String formatTencentTime(String raw) {
        if (!StringUtils.hasText(raw) || raw.length() != 14) {
            return null;
        }
        return raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8)
            + " " + raw.substring(8, 10) + ":" + raw.substring(10, 12) + ":" + raw.substring(12, 14);
    }

    private InvestmentDividendResponse toDividendRecordResponse(InvestmentDividendRecordEntity entity) {
        InvestmentProductEntity product = productMapper.selectById(entity.getProductId());
        InvestmentDividendResponse response = new InvestmentDividendResponse();
        response.setId(entity.getId());
        response.setProductId(entity.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setActualAmount(entity.getNetAmount());
        response.setDividendPerUnit(entity.getDividendPerUnit());
        response.setStatus(entity.getStatus());
        response.setPaidAt(entity.getPaidAt());
        return response;
    }

    private InvestmentDividendResponse toDividendPlanResponse(InvestmentDividendPlanEntity entity) {
        InvestmentProductEntity product = productMapper.selectById(entity.getProductId());
        InvestmentDividendResponse response = new InvestmentDividendResponse();
        response.setId(entity.getId());
        response.setProductId(entity.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setDividendYear(entity.getDividendYear());
        response.setPayDate(entity.getPayDate());
        response.setDividendPerUnit(entity.getDividendPerUnit());
        response.setStatus(entity.getStatus());
        return response;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal rate(BigDecimal profit, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return profit.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<InvestmentPositionEntity> positions, Function<InvestmentPositionEntity, BigDecimal> getter) {
        return positions.stream().map(getter).filter(item -> item != null).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private String generateTransactionNo() {
        return "IV" + LocalDateTime.now().format(NO_TIME_FORMAT) + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
