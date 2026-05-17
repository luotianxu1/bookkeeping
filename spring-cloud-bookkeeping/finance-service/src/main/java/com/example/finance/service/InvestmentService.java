package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.InvestmentDividendResponse;
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
        return toPositionResponse(positionMapper.selectById(entity.getId()), product, account);
    }

    @Transactional
    public Optional<InvestmentPositionResponse> updatePosition(Long id, InvestmentPositionRequest request) {
        InvestmentPositionEntity entity = positionMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        AccountEntity account = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        InvestmentProductEntity product = request.getProductId() != null
            ? requireProduct(request.getProductId())
            : createOrLoadProduct(request.getProduct());
        fillPosition(entity, request, product.getId());
        positionMapper.updateById(entity);
        return Optional.of(toPositionResponse(positionMapper.selectById(id), product, account));
    }

    public boolean deletePosition(Long id, Long userId) {
        InvestmentPositionEntity entity = positionMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }
        return positionMapper.deleteById(id) > 0;
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
        return toTransactionResponses(transactions);
    }

    @Transactional
    public InvestmentTransactionResponse createTransaction(InvestmentTransactionRequest request) {
        requireInvestmentAccount(request.getUserId(), request.getAccountId());
        InvestmentProductEntity product = requireProduct(request.getProductId());
        InvestmentTransactionEntity entity = new InvestmentTransactionEntity();
        entity.setTransactionNo(generateTransactionNo());
        entity.setUserId(request.getUserId());
        entity.setAccountId(request.getAccountId());
        entity.setPositionId(request.getPositionId());
        entity.setProductId(request.getProductId());
        entity.setTradeType(request.getTradeType());
        entity.setQuantity(defaultZero(request.getQuantity()).setScale(6, RoundingMode.HALF_UP));
        entity.setPrice(defaultZero(request.getPrice()).setScale(6, RoundingMode.HALF_UP));
        entity.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        entity.setFeeAmount(defaultZero(request.getFeeAmount()).setScale(2, RoundingMode.HALF_UP));
        entity.setTaxAmount(defaultZero(request.getTaxAmount()).setScale(2, RoundingMode.HALF_UP));
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setTradeAt(request.getTradeAt());
        entity.setStatus(NORMAL_STATUS);
        entity.setRemark(request.getRemark());
        transactionMapper.insert(entity);
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

    private String extractJsonpObject(String body) {
        int start = body == null ? -1 : body.indexOf('{');
        int end = body == null ? -1 : body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("外部产品数据格式错误");
        }
        return body.substring(start, end + 1);
    }

    private BigDecimal decimalText(String first, String second) {
        String value = StringUtils.hasText(first) && !"--".equals(first) ? first : second;
        if (!StringUtils.hasText(value) || "--".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
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
