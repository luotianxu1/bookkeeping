package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.SalaryAccountPageResponse;
import com.example.finance.dto.SalaryAccountRecordRequest;
import com.example.finance.dto.SalaryInitialBalanceRequest;
import com.example.finance.dto.SalaryMonthPageResponse;
import com.example.finance.dto.SalaryMonthRecordRequest;
import com.example.finance.dto.SalaryOverviewResponse;
import com.example.finance.dto.SalarySettingsRequest;
import com.example.finance.dto.SalarySettingsResponse;
import com.example.finance.dto.SalaryTaxPageResponse;
import com.example.finance.entity.SalaryAccountRecordEntity;
import com.example.finance.entity.SalaryMonthRecordEntity;
import com.example.finance.entity.SalaryProfileEntity;
import com.example.finance.entity.SalarySpecialDeductionEntity;
import com.example.finance.mapper.SalaryAccountRecordMapper;
import com.example.finance.mapper.SalaryMonthRecordMapper;
import com.example.finance.mapper.SalaryProfileMapper;
import com.example.finance.mapper.SalarySpecialDeductionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalaryService {

    private static final BigDecimal DEFAULT_MEDICAL_FIXED_AMOUNT = new BigDecimal("22.00");
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String STATUS_ACTIVE = "active";
    private static final String ACCOUNT_SOCIAL = "social_security";
    private static final String ACCOUNT_HOUSING = "housing_fund";
    private static final String ACCOUNT_MEDICAL = "medical";
    private static final String RECORD_INITIAL = "initial";
    private static final String RECORD_AUTO = "auto";
    private static final String RECORD_MANUAL = "manual";
    private static final String AUTO_RECORD_DELETED_NOTE = "系统自动记录已删除";
    private static final DateTimeFormatter MONTH_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Set<String> ACCOUNT_TYPES = Set.of(ACCOUNT_SOCIAL, ACCOUNT_HOUSING, ACCOUNT_MEDICAL);
    private static final BigDecimal DEFAULT_HOUSING_FUND_PERSONAL_RATE = new BigDecimal("12.0000");
    private static final BigDecimal DEFAULT_HOUSING_FUND_COMPANY_RATE = new BigDecimal("12.0000");
    private static final BigDecimal DEFAULT_PENSION_PERSONAL_RATE = new BigDecimal("8.0000");
    private static final BigDecimal DEFAULT_PENSION_COMPANY_RATE = new BigDecimal("16.0000");
    private static final BigDecimal DEFAULT_MEDICAL_PERSONAL_RATE = new BigDecimal("2.0000");
    private static final BigDecimal DEFAULT_MEDICAL_COMPANY_RATE = new BigDecimal("10.0000");
    private static final BigDecimal DEFAULT_UNEMPLOYMENT_PERSONAL_RATE = new BigDecimal("0.5000");
    private static final BigDecimal DEFAULT_UNEMPLOYMENT_COMPANY_RATE = new BigDecimal("0.5000");
    private static final BigDecimal HOUSING_FUND_INTEREST_ANNUAL_RATE = new BigDecimal("1.5000");

    private final SalaryProfileMapper salaryProfileMapper;
    private final SalarySpecialDeductionMapper salarySpecialDeductionMapper;
    private final SalaryAccountRecordMapper salaryAccountRecordMapper;
    private final SalaryMonthRecordMapper salaryMonthRecordMapper;

    public SalaryService(
        SalaryProfileMapper salaryProfileMapper,
        SalarySpecialDeductionMapper salarySpecialDeductionMapper,
        SalaryAccountRecordMapper salaryAccountRecordMapper,
        SalaryMonthRecordMapper salaryMonthRecordMapper
    ) {
        this.salaryProfileMapper = salaryProfileMapper;
        this.salarySpecialDeductionMapper = salarySpecialDeductionMapper;
        this.salaryAccountRecordMapper = salaryAccountRecordMapper;
        this.salaryMonthRecordMapper = salaryMonthRecordMapper;
    }

    public SalaryOverviewResponse getOverview(Long userId, String month) {
        SalaryProfileEntity profile = ensureProfile(userId);
        int year = resolveYear(month);
        ensureYearData(userId, profile, year);
        SalarySpecialDeductionEntity deduction = ensureSpecialDeduction(userId, year);
        int paidMonths = resolvePaidMonths(month, year, profile.getPayDay());
        int overviewMonth = resolveOverviewMonth(month, year);
        SalaryComputation computation = compute(userId, profile, deduction, year, overviewMonth);
        SalaryComputation annualComputation = compute(userId, profile, deduction, year, paidMonths);

        SalaryOverviewResponse response = new SalaryOverviewResponse();
        response.setMonthKey(resolveMonthValue(month, profile.getPayDay()));
        response.setPayDay(profile.getPayDay());
        response.setPaidMonths(annualComputation.paidMonths);
        response.setGrossIncome(scale(computation.grossMonthlyIncome));
        response.setNetIncome(scale(computation.currentMonthTakeHome));
        response.setTotalDeduction(scale(computation.personalDeductionMonthly));
        response.setTaxAmount(scale(computation.currentMonthTax));
        response.setAnnualIncome(scale(annualComputation.annualIncome));
        response.setNetRate(scalePercent(computation.netRate));

        response.setMetrics(List.of(
            metric("税前工资", computation.grossMonthlyIncome),
            metric("五险一金", computation.personalDeductionMonthly),
            metric("个人所得税", computation.currentMonthTax)
        ));

        response.setDetails(List.of(
            detail("公积金", computation.housingFundPersonal.add(computation.housingFundCompany), "个人 " + formatCurrency(computation.housingFundPersonal) + " · 单位 " + formatCurrency(computation.housingFundCompany)),
            detail("养老保险", computation.pensionPersonal.add(computation.pensionCompany), "个人 " + formatCurrency(computation.pensionPersonal) + " · 单位 " + formatCurrency(computation.pensionCompany)),
            detail("医疗保险", computation.medicalPersonal.add(computation.medicalCompany), "个人 " + formatCurrency(computation.medicalPersonal) + " · 单位 " + formatCurrency(computation.medicalCompany)),
            detail("失业保险", computation.unemploymentPersonal.add(computation.unemploymentCompany), "个人 " + formatCurrency(computation.unemploymentPersonal) + " · 单位 " + formatCurrency(computation.unemploymentCompany)),
            detail("大额医疗", computation.medicalFixedAmount, "每月固定扣除"),
            detail("个人所得税", computation.currentMonthTax, "专项附加扣除 " + formatCurrency(computation.monthlySpecialDeductionTotal))
        ));

        response.setLinkedAccounts(List.of(
            linkedAccount(userId, ACCOUNT_SOCIAL, "社保账户", "/finance/salary/accounts/social-security", year, computation.socialAccountMonthlyIncrease),
            linkedAccount(userId, ACCOUNT_HOUSING, "公积金账户", "/finance/salary/accounts/housing-fund", year, computation.housingFundMonthlyIncrease),
            linkedAccount(userId, ACCOUNT_MEDICAL, "医保账户", "/finance/salary/accounts/medical", year, computation.medicalMonthlyIncrease)
        ));

        SalaryOverviewResponse.TaxSummary taxSummary = new SalaryOverviewResponse.TaxSummary();
        taxSummary.setCurrentMonthTax(scale(computation.currentMonthTax));
        taxSummary.setAnnualTax(scale(annualComputation.annualTax));
        taxSummary.setAnnualIncome(scale(annualComputation.annualIncome));
        taxSummary.setRoutePath("/finance/salary/tax");
        response.setTaxSummary(taxSummary);
        return response;
    }

    public SalarySettingsResponse getSettings(Long userId, Integer taxYear) {
        SalaryProfileEntity profile = ensureProfile(userId);
        int year = taxYear == null ? LocalDate.now(SHANGHAI_ZONE).getYear() : taxYear;
        ensureYearData(userId, profile, year);
        SalarySpecialDeductionEntity deduction = ensureSpecialDeduction(userId, year);
        SalaryComputation computation = compute(userId, profile, deduction, year, resolveCurrentPaidMonths(year, profile.getPayDay()));
        return toSettingsResponse(profile, deduction, year, computation);
    }

    @Transactional
    public SalarySettingsResponse saveSettings(SalarySettingsRequest request) {
        SalaryProfileEntity profile = ensureProfile(request.getUserId());
        fillProfile(profile, request);
        profile.setUpdatedAt(LocalDateTime.now(SHANGHAI_ZONE));
        salaryProfileMapper.updateById(profile);

        SalarySpecialDeductionEntity deduction = ensureSpecialDeduction(request.getUserId(), request.getTaxYear());
        fillDeduction(deduction, request);
        if (deduction.getId() == null) {
            salarySpecialDeductionMapper.insert(deduction);
        } else {
            deduction.setUpdatedAt(LocalDateTime.now(SHANGHAI_ZONE));
            salarySpecialDeductionMapper.updateById(deduction);
        }

        refreshLatestDueMonthData(request.getUserId(), profile, request.getTaxYear());
        return toSettingsResponse(profile, deduction, request.getTaxYear(), compute(request.getUserId(), profile, deduction, request.getTaxYear(), resolveCurrentPaidMonths(request.getTaxYear(), profile.getPayDay())));
    }

    public SalaryMonthPageResponse getSalaryMonthPage(Long userId, Integer year) {
        SalaryProfileEntity profile = ensureProfile(userId);
        int resolvedYear = year == null ? LocalDate.now(SHANGHAI_ZONE).getYear() : year;
        return buildSalaryMonthPage(userId, resolvedYear, profile);
    }

    @Transactional
    public SalaryMonthPageResponse createSalaryMonthRecord(SalaryMonthRecordRequest request) {
        SalaryProfileEntity profile = ensureProfile(request.getUserId());
        LocalDate salaryMonth = normalizeMonth(request.getSalaryMonth());
        SalaryMonthRecordEntity existing = salaryMonthRecordMapper.selectOne(new LambdaQueryWrapper<SalaryMonthRecordEntity>()
            .eq(SalaryMonthRecordEntity::getUserId, request.getUserId())
            .eq(SalaryMonthRecordEntity::getSalaryMonth, salaryMonth)
            .last("LIMIT 1"));
        if (existing == null) {
            existing = new SalaryMonthRecordEntity();
            existing.setUserId(request.getUserId());
            existing.setSalaryMonth(salaryMonth);
            existing.setGrossSalary(scale(request.getGrossSalary()));
            existing.setNote(resolveSalaryMonthNote(request.getNote()));
            salaryMonthRecordMapper.insert(existing);
        } else {
            existing.setGrossSalary(scale(request.getGrossSalary()));
            existing.setNote(resolveSalaryMonthNote(request.getNote()));
            salaryMonthRecordMapper.updateById(existing);
        }
        return buildSalaryMonthPage(request.getUserId(), salaryMonth.getYear(), profile);
    }

    @Transactional
    public SalaryMonthPageResponse updateSalaryMonthRecord(Long recordId, SalaryMonthRecordRequest request) {
        SalaryProfileEntity profile = ensureProfile(request.getUserId());
        SalaryMonthRecordEntity entity = requireSalaryMonthRecord(recordId, request.getUserId());
        LocalDate salaryMonth = normalizeMonth(request.getSalaryMonth());
        SalaryMonthRecordEntity duplicate = salaryMonthRecordMapper.selectOne(new LambdaQueryWrapper<SalaryMonthRecordEntity>()
            .eq(SalaryMonthRecordEntity::getUserId, request.getUserId())
            .eq(SalaryMonthRecordEntity::getSalaryMonth, salaryMonth)
            .ne(SalaryMonthRecordEntity::getId, recordId)
            .last("LIMIT 1"));
        if (duplicate != null) {
            throw new IllegalArgumentException("该月份工资记录已存在");
        }
        entity.setSalaryMonth(salaryMonth);
        entity.setGrossSalary(scale(request.getGrossSalary()));
        entity.setNote(resolveSalaryMonthNote(request.getNote()));
        salaryMonthRecordMapper.updateById(entity);
        return buildSalaryMonthPage(request.getUserId(), salaryMonth.getYear(), profile);
    }

    @Transactional
    public SalaryMonthPageResponse deleteSalaryMonthRecord(Long recordId, Long userId) {
        SalaryProfileEntity profile = ensureProfile(userId);
        SalaryMonthRecordEntity entity = requireSalaryMonthRecord(recordId, userId);
        int year = entity.getSalaryMonth() == null ? LocalDate.now(SHANGHAI_ZONE).getYear() : entity.getSalaryMonth().getYear();
        salaryMonthRecordMapper.deleteById(recordId);
        return buildSalaryMonthPage(userId, year, profile);
    }

    public SalaryAccountPageResponse getAccountPage(Long userId, String accountType, Integer year) {
        String normalizedAccountType = normalizeAccountType(accountType);
        SalaryProfileEntity profile = ensureProfile(userId);
        int resolvedYear = year == null ? LocalDate.now(SHANGHAI_ZONE).getYear() : year;
        ensureYearData(userId, profile, resolvedYear);
        return buildAccountPage(userId, normalizedAccountType, resolvedYear, profile);
    }

    public void settleDueAccountRecordsForAllUsers() {
        int currentYear = LocalDate.now(SHANGHAI_ZONE).getYear();
        List<SalaryProfileEntity> profiles = salaryProfileMapper.selectList(new LambdaQueryWrapper<SalaryProfileEntity>()
            .eq(SalaryProfileEntity::getStatus, STATUS_ACTIVE));
        for (SalaryProfileEntity profile : profiles) {
            if (profile.getUserId() == null) {
                continue;
            }
            ensureYearData(profile.getUserId(), profile, currentYear);
        }
    }

    @Transactional
    public SalaryAccountPageResponse saveInitialBalance(String accountType, SalaryInitialBalanceRequest request) {
        String normalizedAccountType = normalizeAccountType(accountType);
        SalaryProfileEntity profile = ensureProfile(request.getUserId());
        SalaryAccountRecordEntity initialRecord = salaryAccountRecordMapper.selectOne(new LambdaQueryWrapper<SalaryAccountRecordEntity>()
            .eq(SalaryAccountRecordEntity::getUserId, request.getUserId())
            .eq(SalaryAccountRecordEntity::getAccountType, normalizedAccountType)
            .eq(SalaryAccountRecordEntity::getRecordType, RECORD_INITIAL)
            .last("LIMIT 1"));

        if (initialRecord == null) {
            initialRecord = new SalaryAccountRecordEntity();
            initialRecord.setUserId(request.getUserId());
            initialRecord.setAccountType(normalizedAccountType);
            initialRecord.setRecordType(RECORD_INITIAL);
        }

        initialRecord.setRecordMonth(normalizeMonth(request.getRecordMonth()));
        initialRecord.setAmount(scale(request.getAmount()));
        initialRecord.setPersonalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        initialRecord.setCompanyAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        initialRecord.setSyncToCurrent(true);
        initialRecord.setNote(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : "初始值设置");

        if (initialRecord.getId() == null) {
            salaryAccountRecordMapper.insert(initialRecord);
        } else {
            salaryAccountRecordMapper.updateById(initialRecord);
        }

        ensureYearData(request.getUserId(), profile, initialRecord.getRecordMonth().getYear());
        recalculateBalances(request.getUserId(), normalizedAccountType);
        return buildAccountPage(request.getUserId(), normalizedAccountType, initialRecord.getRecordMonth().getYear(), profile);
    }

    @Transactional
    public SalaryAccountPageResponse createAccountRecord(String accountType, SalaryAccountRecordRequest request) {
        String normalizedAccountType = normalizeAccountType(accountType);
        SalaryProfileEntity profile = ensureProfile(request.getUserId());
        SalaryAccountRecordEntity entity = new SalaryAccountRecordEntity();
        entity.setUserId(request.getUserId());
        entity.setAccountType(normalizedAccountType);
        entity.setRecordType(RECORD_MANUAL);
        entity.setRecordMonth(normalizeMonth(request.getRecordMonth()));
        entity.setAmount(scale(request.getAmount()));
        entity.setPersonalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setCompanyAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setSyncToCurrent(true);
        entity.setNote(resolveManualNote(request));
        salaryAccountRecordMapper.insert(entity);

        ensureYearData(request.getUserId(), profile, entity.getRecordMonth().getYear());
        recalculateBalances(request.getUserId(), normalizedAccountType);
        return buildAccountPage(request.getUserId(), normalizedAccountType, entity.getRecordMonth().getYear(), profile);
    }

    @Transactional
    public SalaryAccountPageResponse updateAccountRecord(String accountType, Long recordId, SalaryAccountRecordRequest request) {
        String normalizedAccountType = normalizeAccountType(accountType);
        SalaryProfileEntity profile = ensureProfile(request.getUserId());
        SalaryAccountRecordEntity entity = requireEditableRecord(recordId, request.getUserId(), normalizedAccountType);
        entity.setRecordMonth(normalizeMonth(request.getRecordMonth()));
        entity.setAmount(scale(request.getAmount()));
        entity.setNote(resolveRecordUpdateNote(entity, request));
        salaryAccountRecordMapper.updateById(entity);

        ensureYearData(request.getUserId(), profile, entity.getRecordMonth().getYear());
        recalculateBalances(request.getUserId(), normalizedAccountType);
        return buildAccountPage(request.getUserId(), normalizedAccountType, entity.getRecordMonth().getYear(), profile);
    }

    @Transactional
    public SalaryAccountPageResponse deleteAccountRecord(String accountType, Long recordId, Long userId) {
        String normalizedAccountType = normalizeAccountType(accountType);
        SalaryProfileEntity profile = ensureProfile(userId);
        SalaryAccountRecordEntity entity = requireEditableRecord(recordId, userId, normalizedAccountType);
        int year = entity.getRecordMonth() == null ? LocalDate.now(SHANGHAI_ZONE).getYear() : entity.getRecordMonth().getYear();
        if (RECORD_AUTO.equals(entity.getRecordType())) {
            entity.setAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            entity.setPersonalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            entity.setCompanyAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            entity.setSyncToCurrent(false);
            entity.setNote(AUTO_RECORD_DELETED_NOTE);
            salaryAccountRecordMapper.updateById(entity);
            recalculateBalances(userId, normalizedAccountType);
            return buildAccountPage(userId, normalizedAccountType, year, profile);
        }
        salaryAccountRecordMapper.deleteById(recordId);
        ensureYearData(userId, profile, year);
        recalculateBalances(userId, normalizedAccountType);
        return buildAccountPage(userId, normalizedAccountType, year, profile);
    }

    public SalaryTaxPageResponse getTaxPage(Long userId, Integer year) {
        SalaryProfileEntity profile = ensureProfile(userId);
        int resolvedYear = year == null ? LocalDate.now(SHANGHAI_ZONE).getYear() : year;
        ensureYearData(userId, profile, resolvedYear);
        SalarySpecialDeductionEntity deduction = ensureSpecialDeduction(userId, resolvedYear);
        int paidMonths = resolveCurrentPaidMonths(resolvedYear, profile.getPayDay());
        SalaryComputation computation = compute(userId, profile, deduction, resolvedYear, paidMonths);

        SalaryTaxPageResponse response = new SalaryTaxPageResponse();
        response.setYear(resolvedYear);
        response.setPaidMonths(paidMonths);
        response.setAnnualIncome(scale(computation.annualIncome));
        response.setAnnualTax(scale(computation.annualTax));
        response.setCurrentMonthTax(scale(computation.currentMonthTax));
        response.setAnnualNetIncome(scale(computation.annualNetIncome));
        response.setMonthlyAverageNetIncome(scale(computation.annualNetIncome.divide(BigDecimal.valueOf(Math.max(paidMonths, 1L)), 2, RoundingMode.HALF_UP)));
        response.setSpecialDeductionTotal(scale(computation.monthlySpecialDeductionTotal.multiply(BigDecimal.valueOf(paidMonths))));
        response.setMetrics(List.of(
            taxMetric("本年个税", computation.annualTax),
            taxMetric("本月个税", computation.currentMonthTax),
            taxMetric("专项扣除", computation.monthlySpecialDeductionTotal.multiply(BigDecimal.valueOf(paidMonths))),
            taxMetric("年后净收入", computation.annualNetIncome)
        ));
        response.setDeductions(List.of(
            deductionItem("子女教育", deduction.getChildEducation()),
            deductionItem("继续教育", deduction.getContinuingEducation()),
            deductionItem("住房贷款", deduction.getHousingLoan()),
            deductionItem("住房租金", deduction.getHousingRent()),
            deductionItem("赡养老人", deduction.getElderlyCare()),
            deductionItem("大病医疗", deduction.getSeriousMedical()),
            deductionItem("其他扣除", deduction.getOtherDeduction())
        ));

        List<SalaryTaxPageResponse.MonthTaxItem> monthItems = new ArrayList<>();
        for (int month = 1; month <= paidMonths; month++) {
            SalaryComputation monthComputation = compute(userId, profile, deduction, resolvedYear, month);
            SalaryTaxPageResponse.MonthTaxItem item = new SalaryTaxPageResponse.MonthTaxItem();
            item.setMonthKey(monthKey(resolvedYear, month));
            item.setMonthLabel(resolvedYear + " 年 " + month + " 月");
            item.setGrossIncome(scale(monthComputation.grossMonthlyIncome));
            item.setTaxAmount(scale(monthComputation.currentMonthTax));
            item.setTakeHomeIncome(scale(monthComputation.currentMonthTakeHome));
            item.setStatusText(month == paidMonths ? "已缴纳" : "已归档");
            monthItems.add(item);
        }
        response.setMonthItems(monthItems);
        return response;
    }

    private SalarySettingsResponse toSettingsResponse(
        SalaryProfileEntity profile,
        SalarySpecialDeductionEntity deduction,
        int taxYear,
        SalaryComputation computation
    ) {
        SalarySettingsResponse response = new SalarySettingsResponse();
        response.setId(profile.getId());
        response.setUserId(profile.getUserId());
        response.setMonthlyGrossSalary(scale(profile.getMonthlyGrossSalary()));
        response.setTransportSubsidy(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setMealSubsidy(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setAnnualBonus(scale(profile.getAnnualBonus()));
        response.setPayDay(profile.getPayDay());
        response.setSocialSecurityBase(resolveBase(profile.getSocialSecurityBase(), profile.getMonthlyGrossSalary()));
        response.setHousingFundBase(resolveBase(profile.getHousingFundBase(), profile.getMonthlyGrossSalary()));
        response.setHousingFundPersonalRate(resolveRate(profile.getHousingFundPersonalRate(), DEFAULT_HOUSING_FUND_PERSONAL_RATE));
        response.setHousingFundCompanyRate(resolveRate(profile.getHousingFundCompanyRate(), DEFAULT_HOUSING_FUND_COMPANY_RATE));
        response.setPensionPersonalRate(resolveRate(profile.getPensionPersonalRate(), DEFAULT_PENSION_PERSONAL_RATE));
        response.setPensionCompanyRate(resolveRate(profile.getPensionCompanyRate(), DEFAULT_PENSION_COMPANY_RATE));
        response.setMedicalPersonalRate(resolveRate(profile.getMedicalPersonalRate(), DEFAULT_MEDICAL_PERSONAL_RATE));
        response.setMedicalCompanyRate(resolveRate(profile.getMedicalCompanyRate(), DEFAULT_MEDICAL_COMPANY_RATE));
        response.setMedicalFixedAmount(resolveMedicalFixedAmount(profile.getMedicalFixedAmount()));
        response.setUnemploymentPersonalRate(resolveRate(profile.getUnemploymentPersonalRate(), DEFAULT_UNEMPLOYMENT_PERSONAL_RATE));
        response.setUnemploymentCompanyRate(resolveRate(profile.getUnemploymentCompanyRate(), DEFAULT_UNEMPLOYMENT_COMPANY_RATE));
        response.setTaxFreeThreshold(scale(profile.getTaxFreeThreshold()));
        response.setTaxYear(taxYear);
        response.setChildEducation(scale(deduction.getChildEducation()));
        response.setContinuingEducation(scale(deduction.getContinuingEducation()));
        response.setHousingLoan(scale(deduction.getHousingLoan()));
        response.setHousingRent(scale(deduction.getHousingRent()));
        response.setElderlyCare(scale(deduction.getElderlyCare()));
        response.setSeriousMedical(scale(deduction.getSeriousMedical()));
        response.setOtherDeduction(scale(deduction.getOtherDeduction()));
        response.setRemark(profile.getRemark());
        response.setMonthlyTakeHome(scale(computation.currentMonthTakeHome));
        response.setMonthlyTax(scale(computation.currentMonthTax));
        response.setMonthlySpecialDeductionTotal(scale(computation.monthlySpecialDeductionTotal));
        response.setUpdatedAt(profile.getUpdatedAt());
        return response;
    }

    private SalaryMonthPageResponse buildSalaryMonthPage(Long userId, int year, SalaryProfileEntity profile) {
        SalaryMonthPageResponse response = new SalaryMonthPageResponse();
        List<SalaryMonthRecordEntity> records = loadSalaryMonthRecords(userId, year);
        BigDecimal defaultMonthlyGrossSalary = scale(profile.getMonthlyGrossSalary());
        BigDecimal recordedGrossIncome = records.stream()
            .map(SalaryMonthRecordEntity::getGrossSalary)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), (left, right) -> left.add(defaultZero(right)).setScale(2, RoundingMode.HALF_UP));
        BigDecimal estimatedAnnualGrossIncome = recordedGrossIncome
            .add(defaultMonthlyGrossSalary.multiply(BigDecimal.valueOf(Math.max(12 - records.size(), 0L))))
            .setScale(2, RoundingMode.HALF_UP);

        response.setYear(year);
        response.setDefaultMonthlyGrossSalary(defaultMonthlyGrossSalary);
        response.setRecordedMonths(records.size());
        response.setRecordedGrossIncome(recordedGrossIncome);
        response.setEstimatedAnnualGrossIncome(estimatedAnnualGrossIncome);
        response.setMetrics(List.of(
            salaryMonthMetric("默认月薪", defaultMonthlyGrossSalary),
            salaryMonthMetric("已补录税前", recordedGrossIncome),
            salaryMonthMetric("全年预计", estimatedAnnualGrossIncome)
        ));
        response.setRecords(records.stream()
            .sorted(Comparator
                .comparing(SalaryMonthRecordEntity::getSalaryMonth, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(SalaryMonthRecordEntity::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(SalaryMonthRecordEntity::getId, Comparator.nullsLast(Long::compareTo))
                .reversed())
            .map(record -> {
                SalaryMonthPageResponse.RecordItem item = new SalaryMonthPageResponse.RecordItem();
                item.setId(record.getId());
                item.setMonthKey(record.getSalaryMonth() == null ? "" : MONTH_KEY_FORMATTER.format(record.getSalaryMonth()));
                item.setMonthLabel(record.getSalaryMonth() == null
                    ? "--"
                    : record.getSalaryMonth().getYear() + " 年 " + record.getSalaryMonth().getMonthValue() + " 月");
                item.setGrossSalary(scale(record.getGrossSalary()));
                item.setNote(record.getNote());
                item.setEditable(true);
                return item;
            })
            .toList());
        response.setUpdatedAt(records.isEmpty()
            ? profile.getUpdatedAt()
            : records.stream()
                .map(SalaryMonthRecordEntity::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(profile.getUpdatedAt()));
        return response;
    }

    private SalaryAccountPageResponse buildAccountPage(Long userId, String accountType, int year, SalaryProfileEntity profile) {
        ensureYearData(userId, profile, year);
        SalaryAccountPageResponse response = new SalaryAccountPageResponse();
        List<SalaryAccountRecordEntity> records = loadRecords(userId, accountType);
        List<SalaryAccountRecordEntity> yearRecords = records.stream()
            .filter(record -> record.getRecordMonth() != null && record.getRecordMonth().getYear() == year)
            .sorted(Comparator
                .comparing(SalaryAccountRecordEntity::getRecordMonth)
                .thenComparing(SalaryAccountRecordEntity::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(SalaryAccountRecordEntity::getId))
            .toList();

        SalaryComputation computation = compute(userId, profile, ensureSpecialDeduction(userId, year), year, resolveCurrentPaidMonths(year, profile.getPayDay()));
        BigDecimal currentBalance = records.isEmpty() ? BigDecimal.ZERO : defaultZero(records.get(records.size() - 1).getBalanceAfter());
        BigDecimal initialBalance = records.stream()
            .filter(record -> RECORD_INITIAL.equals(record.getRecordType()))
            .findFirst()
            .map(SalaryAccountRecordEntity::getAmount)
            .orElse(BigDecimal.ZERO);
        SalaryAccountRecordEntity latestAutoRecord = findLatestVisibleAutoRecord(yearRecords);
        BigDecimal displayedMonthlyPersonal = latestAutoRecord == null ? monthlyPersonal(accountType, computation) : defaultZero(latestAutoRecord.getPersonalAmount());
        BigDecimal displayedMonthlyCompany = latestAutoRecord == null ? monthlyCompany(accountType, computation) : defaultZero(latestAutoRecord.getCompanyAmount());
        BigDecimal yearlyIncrease = yearRecords.stream()
            .filter(record -> !RECORD_INITIAL.equals(record.getRecordType()))
            .map(this::recordNetAmount)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        response.setAccountType(accountType);
        response.setTitle(accountTitle(accountType));
        response.setSubtitle(accountSubtitle(accountType));
        response.setBadgeText(accountBadge(accountType));
        response.setYear(year);
        response.setCurrentBalance(scale(currentBalance));
        response.setInitialBalance(scale(initialBalance));
        response.setMonthlyPersonal(scale(displayedMonthlyPersonal));
        response.setMonthlyCompany(scale(displayedMonthlyCompany));
        response.setYearlyIncrease(scale(yearlyIncrease));
        response.setMetrics(List.of(
            metricItem("个人缴存", displayedMonthlyPersonal),
            metricItem("单位缴存", displayedMonthlyCompany),
            metricItem("初始值", initialBalance)
        ));
        response.setDetails(buildAccountDetails(accountType, computation, currentBalance, initialBalance));
        response.setRecords(buildAccountRecords(yearRecords));
        response.setForecast(buildSalaryAccountForecast(userId, accountType, year, profile, records));
        response.setUpdatedAt(records.isEmpty() ? profile.getUpdatedAt() : records.get(records.size() - 1).getUpdatedAt());
        return response;
    }

    private SalaryAccountPageResponse.Forecast buildSalaryAccountForecast(
        Long userId,
        String accountType,
        int year,
        SalaryProfileEntity profile,
        List<SalaryAccountRecordEntity> accountRecords
    ) {
        BigDecimal defaultMonthlyGrossSalary = scale(profile.getMonthlyGrossSalary());
        BigDecimal sourceAnnualGrossIncome = sumMonthlyGrossSalaries(
            resolveMonthlyGrossSalaries(userId, year, 12, defaultMonthlyGrossSalary),
            12
        ).add(defaultZero(profile.getAnnualBonus())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal predictedMonthlyBase = sourceAnnualGrossIncome
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        BigDecimal predictedPersonal;
        BigDecimal predictedCompany;
        if (ACCOUNT_HOUSING.equals(accountType)) {
            predictedPersonal = roundedYuanRateAmount(
                predictedMonthlyBase,
                resolveRate(profile.getHousingFundPersonalRate(), DEFAULT_HOUSING_FUND_PERSONAL_RATE)
            );
            predictedCompany = roundedYuanRateAmount(
                predictedMonthlyBase,
                resolveRate(profile.getHousingFundCompanyRate(), DEFAULT_HOUSING_FUND_COMPANY_RATE)
            );
        } else if (ACCOUNT_MEDICAL.equals(accountType)) {
            predictedPersonal = rateAmount(
                predictedMonthlyBase,
                resolveRate(profile.getMedicalPersonalRate(), DEFAULT_MEDICAL_PERSONAL_RATE)
            );
            predictedCompany = rateAmount(
                predictedMonthlyBase,
                resolveRate(profile.getMedicalCompanyRate(), DEFAULT_MEDICAL_COMPANY_RATE)
            );
        } else {
            predictedPersonal = rateAmount(
                predictedMonthlyBase,
                resolveRate(profile.getPensionPersonalRate(), DEFAULT_PENSION_PERSONAL_RATE)
            );
            predictedCompany = rateAmount(
                predictedMonthlyBase,
                resolveRate(profile.getPensionCompanyRate(), DEFAULT_PENSION_COMPANY_RATE)
            );
        }

        SalaryAccountPageResponse.Forecast forecast = new SalaryAccountPageResponse.Forecast();
        forecast.setSourceYear(year);
        forecast.setForecastYear(year + 1);
        forecast.setSourceAnnualGrossIncome(scale(sourceAnnualGrossIncome));
        forecast.setPredictedMonthlyBase(scale(predictedMonthlyBase));
        forecast.setPredictedPersonal(scale(predictedPersonal));
        forecast.setPredictedCompany(scale(predictedCompany));
        if (ACCOUNT_HOUSING.equals(accountType)) {
            forecast.setPredictedInterest(predictHousingFundInterest(
                accountRecords,
                year,
                predictedPersonal.add(predictedCompany)
            ));
            forecast.setInterestAnnualRate(scaleRate(HOUSING_FUND_INTEREST_ANNUAL_RATE));
            forecast.setInterestSettlementDate(LocalDate.of(year + 1, 7, 1));
        }
        return forecast;
    }

    private BigDecimal predictHousingFundInterest(
        List<SalaryAccountRecordEntity> records,
        int sourceYear,
        BigDecimal predictedMonthlyContribution
    ) {
        YearMonth cycleStart = YearMonth.of(sourceYear, 7);
        YearMonth settlementMonth = YearMonth.of(sourceYear + 1, 7);
        LocalDate cycleStartDate = cycleStart.atDay(1);

        BigDecimal openingBalance = records.stream()
            .filter(record -> record.getRecordMonth() != null)
            .filter(record -> record.getRecordMonth().isBefore(cycleStartDate))
            .filter(record -> !Boolean.FALSE.equals(record.getSyncToCurrent()))
            .map(SalaryAccountRecordEntity::getAmount)
            .map(this::defaultZero)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        Set<YearMonth> recordedAutoMonths = records.stream()
            .filter(record -> RECORD_AUTO.equals(record.getRecordType()))
            .filter(record -> record.getRecordMonth() != null)
            .map(record -> YearMonth.from(record.getRecordMonth()))
            .filter(month -> !month.isBefore(cycleStart) && month.isBefore(settlementMonth))
            .collect(Collectors.toSet());

        BigDecimal weightedBalanceMonths = openingBalance.multiply(BigDecimal.valueOf(12));
        for (SalaryAccountRecordEntity record : records) {
            if (record.getRecordMonth() == null || Boolean.FALSE.equals(record.getSyncToCurrent())) {
                continue;
            }
            YearMonth recordMonth = YearMonth.from(record.getRecordMonth());
            if (recordMonth.isBefore(cycleStart) || !recordMonth.isBefore(settlementMonth)) {
                continue;
            }
            int remainingMonths = monthsBetween(recordMonth, settlementMonth);
            weightedBalanceMonths = weightedBalanceMonths.add(
                defaultZero(record.getAmount()).multiply(BigDecimal.valueOf(remainingMonths))
            );
        }

        BigDecimal monthlyContribution = scale(predictedMonthlyContribution);
        for (YearMonth month = cycleStart; month.isBefore(settlementMonth); month = month.plusMonths(1)) {
            if (recordedAutoMonths.contains(month)) {
                continue;
            }
            int remainingMonths = monthsBetween(month, settlementMonth);
            weightedBalanceMonths = weightedBalanceMonths.add(
                monthlyContribution.multiply(BigDecimal.valueOf(remainingMonths))
            );
        }

        return weightedBalanceMonths
            .multiply(HOUSING_FUND_INTEREST_ANNUAL_RATE)
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private int monthsBetween(YearMonth start, YearMonth end) {
        return (end.getYear() - start.getYear()) * 12 + end.getMonthValue() - start.getMonthValue();
    }

    private List<SalaryAccountPageResponse.DetailItem> buildAccountDetails(
        String accountType,
        SalaryComputation computation,
        BigDecimal currentBalance,
        BigDecimal initialBalance
    ) {
        if (ACCOUNT_HOUSING.equals(accountType)) {
            return List.of(
                accountDetail("公积金个人缴存", "按基数比例自动累计", computation.housingFundPersonal),
                accountDetail("单位缴存", "公司同步缴存部分", computation.housingFundCompany),
                accountDetail("当前账户余额", "含历史初始值与手动调整", currentBalance)
            );
        }
        if (ACCOUNT_MEDICAL.equals(accountType)) {
            return List.of(
                accountDetail("医保个人缴存", "按基数比例自动累计", computation.medicalPersonal),
                accountDetail("统筹划入", "单位缴纳进入医保统筹", computation.medicalCompany),
                accountDetail("初始可用余额", "首次录入后参与累计", initialBalance)
            );
        }
        return List.of(
            accountDetail("养老个人缴存", "按社保基数自动累计", computation.pensionPersonal),
            accountDetail("养老单位缴存", "公司同步缴纳部分，不计入个人账户余额", computation.pensionCompany),
            accountDetail("当前账户余额", "含初始值与月度自动入账", currentBalance)
        );
    }

    private List<SalaryAccountPageResponse.RecordItem> buildAccountRecords(List<SalaryAccountRecordEntity> records) {
        return records.stream()
            .filter(record -> !isDeletedAutoRecord(record))
            .sorted(Comparator
                .comparing(SalaryAccountRecordEntity::getRecordMonth, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(SalaryAccountRecordEntity::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(SalaryAccountRecordEntity::getId, Comparator.nullsLast(Long::compareTo))
                .reversed())
            .limit(12)
            .map(record -> {
                SalaryAccountPageResponse.RecordItem item = new SalaryAccountPageResponse.RecordItem();
                item.setId(record.getId());
                item.setMonthKey(record.getRecordMonth() == null ? "" : MONTH_KEY_FORMATTER.format(record.getRecordMonth()));
                item.setMonthLabel(record.getRecordMonth() == null
                    ? "--"
                    : record.getRecordMonth().getYear() + " 年 " + record.getRecordMonth().getMonthValue() + " 月");
                item.setRecordType(record.getRecordType());
                item.setPillText(resolveRecordPill(record.getRecordType()));
                item.setAmountLabel(RECORD_INITIAL.equals(record.getRecordType()) ? "初始值设置" : RECORD_AUTO.equals(record.getRecordType()) ? "本月缴存" : "手动调整");
                item.setAmountValue(scale(record.getAmount()));
                item.setBalanceLabel("账户余额");
                item.setBalanceValue(scale(record.getBalanceAfter()));
                item.setNote(record.getNote());
                item.setEditable(true);
                return item;
            })
            .toList();
    }

    private boolean isDeletedAutoRecord(SalaryAccountRecordEntity record) {
        return RECORD_AUTO.equals(record.getRecordType())
            && Boolean.FALSE.equals(record.getSyncToCurrent())
            && defaultZero(record.getAmount()).compareTo(BigDecimal.ZERO) == 0
            && Objects.equals(record.getNote(), AUTO_RECORD_DELETED_NOTE);
    }

    private SalaryAccountRecordEntity findLatestVisibleAutoRecord(List<SalaryAccountRecordEntity> records) {
        return records.stream()
            .filter(record -> RECORD_AUTO.equals(record.getRecordType()))
            .filter(record -> !isDeletedAutoRecord(record))
            .max(Comparator
                .comparing(SalaryAccountRecordEntity::getRecordMonth, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(SalaryAccountRecordEntity::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(SalaryAccountRecordEntity::getId, Comparator.nullsLast(Long::compareTo)))
            .orElse(null);
    }

    private SalaryProfileEntity ensureProfile(Long userId) {
        SalaryProfileEntity profile = salaryProfileMapper.selectOne(new LambdaQueryWrapper<SalaryProfileEntity>()
            .eq(SalaryProfileEntity::getUserId, userId)
            .last("LIMIT 1"));
        if (profile != null) {
            return profile;
        }

        profile = defaultProfile(userId);
        salaryProfileMapper.insert(profile);
        return profile;
    }

    private SalaryProfileEntity defaultProfile(Long userId) {
        SalaryProfileEntity profile = new SalaryProfileEntity();
        profile.setUserId(userId);
        profile.setMonthlyGrossSalary(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        profile.setTransportSubsidy(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        profile.setMealSubsidy(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        profile.setAnnualBonus(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        profile.setPayDay(15);
        profile.setSocialSecurityBase(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        profile.setHousingFundBase(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        profile.setHousingFundPersonalRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        profile.setHousingFundCompanyRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        profile.setPensionPersonalRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        profile.setPensionCompanyRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        profile.setMedicalPersonalRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        profile.setMedicalCompanyRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        profile.setMedicalFixedAmount(DEFAULT_MEDICAL_FIXED_AMOUNT);
        profile.setUnemploymentPersonalRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        profile.setUnemploymentCompanyRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        profile.setTaxFreeThreshold(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        profile.setStatus(STATUS_ACTIVE);
        profile.setRemark(null);
        return profile;
    }

    private SalarySpecialDeductionEntity ensureSpecialDeduction(Long userId, int year) {
        SalarySpecialDeductionEntity entity = salarySpecialDeductionMapper.selectOne(new LambdaQueryWrapper<SalarySpecialDeductionEntity>()
            .eq(SalarySpecialDeductionEntity::getUserId, userId)
            .eq(SalarySpecialDeductionEntity::getTaxYear, year)
            .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }

        entity = new SalarySpecialDeductionEntity();
        entity.setUserId(userId);
        entity.setTaxYear(year);
        entity.setChildEducation(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setContinuingEducation(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setHousingLoan(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setHousingRent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setElderlyCare(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setSeriousMedical(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setOtherDeduction(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        salarySpecialDeductionMapper.insert(entity);
        return entity;
    }

    private void ensureYearData(Long userId, SalaryProfileEntity profile, int year) {
        ensureAutoRecord(userId, profile, ACCOUNT_SOCIAL, year);
        ensureAutoRecord(userId, profile, ACCOUNT_HOUSING, year);
        ensureAutoRecord(userId, profile, ACCOUNT_MEDICAL, year);
        recalculateBalances(userId, ACCOUNT_SOCIAL);
        recalculateBalances(userId, ACCOUNT_HOUSING);
        recalculateBalances(userId, ACCOUNT_MEDICAL);
    }

    private void refreshLatestDueMonthData(Long userId, SalaryProfileEntity profile, int year) {
        int dueMonth = shouldMaintainAutoRecordsForYear(year)
            ? resolveCurrentPaidMonths(year, profile.getPayDay())
            : 0;
        ensureAutoRecord(userId, profile, ACCOUNT_SOCIAL, year, dueMonth);
        ensureAutoRecord(userId, profile, ACCOUNT_HOUSING, year, dueMonth);
        ensureAutoRecord(userId, profile, ACCOUNT_MEDICAL, year, dueMonth);
        recalculateBalances(userId, ACCOUNT_SOCIAL);
        recalculateBalances(userId, ACCOUNT_HOUSING);
        recalculateBalances(userId, ACCOUNT_MEDICAL);
    }

    private void ensureAutoRecord(Long userId, SalaryProfileEntity profile, String accountType, int year) {
        ensureAutoRecord(userId, profile, accountType, year, 0);
    }

    private void ensureAutoRecord(Long userId, SalaryProfileEntity profile, String accountType, int year, int refreshMonth) {
        if (!shouldMaintainAutoRecordsForYear(year)) {
            return;
        }
        int endMonth = resolveCurrentPaidMonths(year, profile.getPayDay());
        if (endMonth <= 0) {
            salaryAccountRecordMapper.delete(new LambdaQueryWrapper<SalaryAccountRecordEntity>()
                .eq(SalaryAccountRecordEntity::getUserId, userId)
                .eq(SalaryAccountRecordEntity::getAccountType, accountType)
                .eq(SalaryAccountRecordEntity::getRecordType, RECORD_AUTO)
                .ge(SalaryAccountRecordEntity::getRecordMonth, LocalDate.of(year, 1, 1))
                .lt(SalaryAccountRecordEntity::getRecordMonth, LocalDate.of(year + 1, 1, 1)));
            return;
        }

        salaryAccountRecordMapper.delete(new LambdaQueryWrapper<SalaryAccountRecordEntity>()
            .eq(SalaryAccountRecordEntity::getUserId, userId)
            .eq(SalaryAccountRecordEntity::getAccountType, accountType)
            .eq(SalaryAccountRecordEntity::getRecordType, RECORD_AUTO)
            .gt(SalaryAccountRecordEntity::getRecordMonth, LocalDate.of(year, endMonth, 1))
            .lt(SalaryAccountRecordEntity::getRecordMonth, LocalDate.of(year + 1, 1, 1)));

        Map<String, SalaryAccountRecordEntity> existingMap = salaryAccountRecordMapper.selectList(new LambdaQueryWrapper<SalaryAccountRecordEntity>()
                .eq(SalaryAccountRecordEntity::getUserId, userId)
                .eq(SalaryAccountRecordEntity::getAccountType, accountType)
                .eq(SalaryAccountRecordEntity::getRecordType, RECORD_AUTO))
            .stream()
            .filter(record -> record.getRecordMonth() != null && record.getRecordMonth().getYear() == year)
            .collect(Collectors.toMap(
                record -> MONTH_KEY_FORMATTER.format(record.getRecordMonth()),
                record -> record,
                (left, right) -> left
            ));

        String note = switch (accountType) {
            case ACCOUNT_HOUSING -> "系统按月自动累计公积金";
            case ACCOUNT_MEDICAL -> "系统按月自动累计医保个账";
            default -> "系统按月自动累计社保账户";
        };

        SalarySpecialDeductionEntity deduction = ensureSpecialDeduction(userId, year);
        for (int month = 1; month <= endMonth; month++) {
            LocalDate recordMonth = LocalDate.of(year, month, 1);
            String key = MONTH_KEY_FORMATTER.format(recordMonth);
            SalaryAccountRecordEntity entity = existingMap.get(key);
            if (entity != null && (month != refreshMonth || isDeletedAutoRecord(entity))) {
                continue;
            }

            SalaryComputation computation = compute(userId, profile, deduction, year, month);
            BigDecimal personal = monthlyPersonal(accountType, computation);
            BigDecimal company = monthlyCompany(accountType, computation);
            BigDecimal amount = autoRecordAmount(accountType, personal, company);
            if (entity == null && amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (entity == null) {
                entity = new SalaryAccountRecordEntity();
                entity.setUserId(userId);
                entity.setAccountType(accountType);
                entity.setRecordType(RECORD_AUTO);
                entity.setBalanceAfter(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
            entity.setRecordMonth(recordMonth);
            entity.setAmount(amount);
            entity.setPersonalAmount(personal);
            entity.setCompanyAmount(company);
            entity.setSyncToCurrent(true);
            entity.setNote(note);
            entity.setUpdatedAt(LocalDateTime.now(SHANGHAI_ZONE));
            if (entity.getId() == null) {
                salaryAccountRecordMapper.insert(entity);
            } else {
                salaryAccountRecordMapper.updateById(entity);
            }
        }
    }

    private boolean shouldMaintainAutoRecordsForYear(int year) {
        return year == LocalDate.now(SHANGHAI_ZONE).getYear();
    }

    private void recalculateBalances(Long userId, String accountType) {
        List<SalaryAccountRecordEntity> records = loadRecords(userId, accountType);
        BigDecimal running = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (SalaryAccountRecordEntity record : records) {
            if (!Boolean.FALSE.equals(record.getSyncToCurrent())) {
                running = running.add(defaultZero(record.getAmount())).setScale(2, RoundingMode.HALF_UP);
            }
            if (record.getBalanceAfter() == null || record.getBalanceAfter().compareTo(running) != 0) {
                record.setBalanceAfter(running);
                salaryAccountRecordMapper.updateById(record);
            }
        }
    }

    private List<SalaryAccountRecordEntity> loadRecords(Long userId, String accountType) {
        return salaryAccountRecordMapper.selectList(new LambdaQueryWrapper<SalaryAccountRecordEntity>()
                .eq(SalaryAccountRecordEntity::getUserId, userId)
                .eq(SalaryAccountRecordEntity::getAccountType, accountType)
                .orderByAsc(SalaryAccountRecordEntity::getRecordMonth)
                .orderByAsc(SalaryAccountRecordEntity::getCreatedAt)
                .orderByAsc(SalaryAccountRecordEntity::getId))
            .stream()
            .sorted(Comparator
                .comparing(SalaryAccountRecordEntity::getRecordMonth, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(SalaryAccountRecordEntity::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(SalaryAccountRecordEntity::getId, Comparator.nullsLast(Long::compareTo)))
            .toList();
    }

    private SalaryAccountRecordEntity requireEditableRecord(Long recordId, Long userId, String accountType) {
        SalaryAccountRecordEntity entity = salaryAccountRecordMapper.selectById(recordId);
        if (entity == null || !Objects.equals(entity.getUserId(), userId) || !Objects.equals(entity.getAccountType(), accountType)) {
            throw new IllegalArgumentException("工资账户记录不存在");
        }
        return entity;
    }

    private SalaryMonthRecordEntity requireSalaryMonthRecord(Long recordId, Long userId) {
        SalaryMonthRecordEntity entity = salaryMonthRecordMapper.selectById(recordId);
        if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
            throw new IllegalArgumentException("工资明细记录不存在");
        }
        return entity;
    }

    private void fillProfile(SalaryProfileEntity profile, SalarySettingsRequest request) {
        profile.setUserId(request.getUserId());
        profile.setMonthlyGrossSalary(scale(request.getMonthlyGrossSalary()));
        profile.setTransportSubsidy(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        profile.setMealSubsidy(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        profile.setAnnualBonus(scale(request.getAnnualBonus()));
        profile.setPayDay(request.getPayDay());
        profile.setSocialSecurityBase(resolveBase(request.getSocialSecurityBase(), request.getMonthlyGrossSalary()));
        profile.setHousingFundBase(resolveBase(request.getHousingFundBase(), request.getMonthlyGrossSalary()));
        profile.setHousingFundPersonalRate(resolveRate(request.getHousingFundPersonalRate(), DEFAULT_HOUSING_FUND_PERSONAL_RATE));
        profile.setHousingFundCompanyRate(resolveRate(request.getHousingFundCompanyRate(), DEFAULT_HOUSING_FUND_COMPANY_RATE));
        profile.setPensionPersonalRate(resolveRate(request.getPensionPersonalRate(), DEFAULT_PENSION_PERSONAL_RATE));
        profile.setPensionCompanyRate(resolveRate(request.getPensionCompanyRate(), DEFAULT_PENSION_COMPANY_RATE));
        profile.setMedicalPersonalRate(resolveRate(request.getMedicalPersonalRate(), DEFAULT_MEDICAL_PERSONAL_RATE));
        profile.setMedicalCompanyRate(resolveRate(request.getMedicalCompanyRate(), DEFAULT_MEDICAL_COMPANY_RATE));
        profile.setMedicalFixedAmount(resolveMedicalFixedAmount(request.getMedicalFixedAmount()));
        profile.setUnemploymentPersonalRate(resolveRate(request.getUnemploymentPersonalRate(), DEFAULT_UNEMPLOYMENT_PERSONAL_RATE));
        profile.setUnemploymentCompanyRate(resolveRate(request.getUnemploymentCompanyRate(), DEFAULT_UNEMPLOYMENT_COMPANY_RATE));
        profile.setTaxFreeThreshold(scale(request.getTaxFreeThreshold()));
        profile.setStatus(STATUS_ACTIVE);
        profile.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
    }

    private void fillDeduction(SalarySpecialDeductionEntity entity, SalarySettingsRequest request) {
        entity.setUserId(request.getUserId());
        entity.setTaxYear(request.getTaxYear());
        entity.setChildEducation(scale(request.getChildEducation()));
        entity.setContinuingEducation(scale(request.getContinuingEducation()));
        entity.setHousingLoan(scale(request.getHousingLoan()));
        entity.setHousingRent(scale(request.getHousingRent()));
        entity.setElderlyCare(scale(request.getElderlyCare()));
        entity.setSeriousMedical(scale(request.getSeriousMedical()));
        entity.setOtherDeduction(scale(request.getOtherDeduction()));
    }

    private SalaryComputation compute(
        Long userId,
        SalaryProfileEntity profile,
        SalarySpecialDeductionEntity deduction,
        int year,
        int paidMonths
    ) {
        SalaryComputation computation = new SalaryComputation();
        computation.paidMonths = Math.max(1, Math.min(paidMonths, 12));
        BigDecimal defaultMonthlyGrossSalary = scale(profile.getMonthlyGrossSalary());
        List<BigDecimal> monthlyGrossSalaries = resolveMonthlyGrossSalaries(userId, year, computation.paidMonths, defaultMonthlyGrossSalary);
        BigDecimal monthlyGrossSalary = monthlyGrossSalaries.get(computation.paidMonths - 1);
        BigDecimal socialSecurityBase = resolveBase(profile.getSocialSecurityBase(), monthlyGrossSalary);
        BigDecimal housingFundBase = resolveBase(profile.getHousingFundBase(), monthlyGrossSalary);
        BigDecimal housingFundPersonalRate = resolveRate(profile.getHousingFundPersonalRate(), DEFAULT_HOUSING_FUND_PERSONAL_RATE);
        BigDecimal housingFundCompanyRate = resolveRate(profile.getHousingFundCompanyRate(), DEFAULT_HOUSING_FUND_COMPANY_RATE);
        BigDecimal pensionPersonalRate = resolveRate(profile.getPensionPersonalRate(), DEFAULT_PENSION_PERSONAL_RATE);
        BigDecimal pensionCompanyRate = resolveRate(profile.getPensionCompanyRate(), DEFAULT_PENSION_COMPANY_RATE);
        BigDecimal medicalPersonalRate = resolveRate(profile.getMedicalPersonalRate(), DEFAULT_MEDICAL_PERSONAL_RATE);
        BigDecimal medicalCompanyRate = resolveRate(profile.getMedicalCompanyRate(), DEFAULT_MEDICAL_COMPANY_RATE);
        BigDecimal medicalFixedAmount = resolveMedicalFixedAmount(profile.getMedicalFixedAmount());
        BigDecimal unemploymentPersonalRate = resolveRate(profile.getUnemploymentPersonalRate(), DEFAULT_UNEMPLOYMENT_PERSONAL_RATE);
        BigDecimal unemploymentCompanyRate = resolveRate(profile.getUnemploymentCompanyRate(), DEFAULT_UNEMPLOYMENT_COMPANY_RATE);

        computation.grossMonthlyIncome = monthlyGrossSalary;

        computation.housingFundPersonal = roundedYuanRateAmount(housingFundBase, housingFundPersonalRate);
        computation.housingFundCompany = roundedYuanRateAmount(housingFundBase, housingFundCompanyRate);
        computation.pensionPersonal = rateAmount(socialSecurityBase, pensionPersonalRate);
        computation.pensionCompany = rateAmount(socialSecurityBase, pensionCompanyRate);
        computation.medicalPersonal = rateAmount(socialSecurityBase, medicalPersonalRate);
        computation.medicalCompany = rateAmount(socialSecurityBase, medicalCompanyRate);
        computation.medicalFixedAmount = medicalFixedAmount;
        computation.unemploymentPersonal = rateAmount(socialSecurityBase, unemploymentPersonalRate);
        computation.unemploymentCompany = rateAmount(socialSecurityBase, unemploymentCompanyRate);

        computation.monthlySpecialDeductionTotal = defaultZero(deduction.getChildEducation())
            .add(defaultZero(deduction.getContinuingEducation()))
            .add(defaultZero(deduction.getHousingLoan()))
            .add(defaultZero(deduction.getHousingRent()))
            .add(defaultZero(deduction.getElderlyCare()))
            .add(defaultZero(deduction.getSeriousMedical()))
            .add(defaultZero(deduction.getOtherDeduction()))
            .setScale(2, RoundingMode.HALF_UP);

        List<BigDecimal> monthlyPersonalDeductions = resolveMonthlyPersonalDeductions(
            userId,
            profile,
            year,
            computation.paidMonths,
            monthlyGrossSalaries
        );
        BigDecimal currentMonthPersonalDeduction = monthlyPersonalDeductions.get(computation.paidMonths - 1);
        BigDecimal paidPersonalDeduction = sumMonthlyGrossSalaries(monthlyPersonalDeductions, computation.paidMonths);
        computation.personalDeductionMonthly = currentMonthPersonalDeduction;

        BigDecimal paidGrossIncome = sumMonthlyGrossSalaries(monthlyGrossSalaries, computation.paidMonths);
        BigDecimal annualIncome = paidGrossIncome
            .add(defaultZero(profile.getAnnualBonus()))
            .setScale(2, RoundingMode.HALF_UP);
        computation.annualIncome = annualIncome;

        BigDecimal annualTaxableIncome = annualIncome
            .subtract(paidPersonalDeduction)
            .subtract(computation.monthlySpecialDeductionTotal.multiply(BigDecimal.valueOf(computation.paidMonths)))
            .subtract(defaultZero(profile.getTaxFreeThreshold()).multiply(BigDecimal.valueOf(computation.paidMonths)))
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualTax = calculateIndividualTax(annualTaxableIncome);
        computation.annualTax = annualTax;

        if (computation.paidMonths <= 1) {
            computation.currentMonthTax = annualTax;
        } else {
            BigDecimal previousAnnualIncome = sumMonthlyGrossSalaries(monthlyGrossSalaries, computation.paidMonths - 1)
                .add(defaultZero(profile.getAnnualBonus()))
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal previousPersonalDeduction = sumMonthlyGrossSalaries(monthlyPersonalDeductions, computation.paidMonths - 1);
            BigDecimal previousTaxableIncome = previousAnnualIncome
                .subtract(previousPersonalDeduction)
                .subtract(computation.monthlySpecialDeductionTotal.multiply(BigDecimal.valueOf(computation.paidMonths - 1L)))
                .subtract(defaultZero(profile.getTaxFreeThreshold()).multiply(BigDecimal.valueOf(computation.paidMonths - 1L)))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
            computation.currentMonthTax = annualTax.subtract(calculateIndividualTax(previousTaxableIncome)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        computation.currentMonthTakeHome = computation.grossMonthlyIncome
            .subtract(currentMonthPersonalDeduction)
            .subtract(computation.currentMonthTax)
            .setScale(2, RoundingMode.HALF_UP);
        computation.annualNetIncome = annualIncome
            .subtract(paidPersonalDeduction)
            .subtract(annualTax)
            .setScale(2, RoundingMode.HALF_UP);
        computation.netRate = computation.grossMonthlyIncome.compareTo(BigDecimal.ZERO) <= 0
            ? BigDecimal.ZERO
            : computation.currentMonthTakeHome
                .divide(computation.grossMonthlyIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        computation.socialAccountMonthlyIncrease = computation.pensionPersonal.setScale(2, RoundingMode.HALF_UP);
        computation.housingFundMonthlyIncrease = computation.housingFundPersonal.add(computation.housingFundCompany).setScale(2, RoundingMode.HALF_UP);
        computation.medicalMonthlyIncrease = computation.medicalPersonal.setScale(2, RoundingMode.HALF_UP);
        return computation;
    }

    private BigDecimal calculateIndividualTax(BigDecimal taxableIncome) {
        BigDecimal income = defaultZero(taxableIncome).max(BigDecimal.ZERO);
        if (income.compareTo(decimal("36000")) <= 0) {
            return income.multiply(decimal("0.03")).setScale(2, RoundingMode.HALF_UP);
        }
        if (income.compareTo(decimal("144000")) <= 0) {
            return income.multiply(decimal("0.10")).subtract(decimal("2520")).setScale(2, RoundingMode.HALF_UP);
        }
        if (income.compareTo(decimal("300000")) <= 0) {
            return income.multiply(decimal("0.20")).subtract(decimal("16920")).setScale(2, RoundingMode.HALF_UP);
        }
        if (income.compareTo(decimal("420000")) <= 0) {
            return income.multiply(decimal("0.25")).subtract(decimal("31920")).setScale(2, RoundingMode.HALF_UP);
        }
        if (income.compareTo(decimal("660000")) <= 0) {
            return income.multiply(decimal("0.30")).subtract(decimal("52920")).setScale(2, RoundingMode.HALF_UP);
        }
        if (income.compareTo(decimal("960000")) <= 0) {
            return income.multiply(decimal("0.35")).subtract(decimal("85920")).setScale(2, RoundingMode.HALF_UP);
        }
        return income.multiply(decimal("0.45")).subtract(decimal("181920")).setScale(2, RoundingMode.HALF_UP);
    }

    private SalaryOverviewResponse.MetricItem metric(String label, BigDecimal value) {
        SalaryOverviewResponse.MetricItem item = new SalaryOverviewResponse.MetricItem();
        item.setLabel(label);
        item.setValue(scale(value));
        return item;
    }

    private SalaryOverviewResponse.DetailItem detail(String label, BigDecimal value, String detail) {
        SalaryOverviewResponse.DetailItem item = new SalaryOverviewResponse.DetailItem();
        item.setLabel(label);
        item.setValue(scale(value));
        item.setDetail(detail);
        return item;
    }

    private SalaryOverviewResponse.AccountSummary linkedAccount(
        Long userId,
        String accountType,
        String title,
        String routePath,
        int year,
        BigDecimal monthlyDeposit
    ) {
        SalaryOverviewResponse.AccountSummary item = new SalaryOverviewResponse.AccountSummary();
        item.setAccountType(accountType);
        item.setTitle(title);
        item.setCurrentBalance(scale(latestAccountBalance(userId, accountType, year)));
        item.setMonthlyDeposit(scale(monthlyDeposit));
        item.setRoutePath(routePath);
        return item;
    }

    private SalaryMonthPageResponse.MetricItem salaryMonthMetric(String label, BigDecimal value) {
        SalaryMonthPageResponse.MetricItem item = new SalaryMonthPageResponse.MetricItem();
        item.setLabel(label);
        item.setValue(scale(value));
        return item;
    }

    private SalaryAccountPageResponse.MetricItem metricItem(String label, BigDecimal value) {
        SalaryAccountPageResponse.MetricItem item = new SalaryAccountPageResponse.MetricItem();
        item.setLabel(label);
        item.setValue(scale(value));
        return item;
    }

    private SalaryAccountPageResponse.DetailItem accountDetail(String label, String description, BigDecimal value) {
        SalaryAccountPageResponse.DetailItem item = new SalaryAccountPageResponse.DetailItem();
        item.setLabel(label);
        item.setDescription(description);
        item.setValue(scale(value));
        return item;
    }

    private SalaryTaxPageResponse.MetricItem taxMetric(String label, BigDecimal value) {
        SalaryTaxPageResponse.MetricItem item = new SalaryTaxPageResponse.MetricItem();
        item.setLabel(label);
        item.setValue(scale(value));
        return item;
    }

    private SalaryTaxPageResponse.DeductionItem deductionItem(String label, BigDecimal monthlyValue) {
        SalaryTaxPageResponse.DeductionItem item = new SalaryTaxPageResponse.DeductionItem();
        item.setLabel(label);
        item.setMonthlyValue(scale(monthlyValue));
        item.setAnnualValue(scale(defaultZero(monthlyValue).multiply(BigDecimal.valueOf(12))));
        return item;
    }

    private BigDecimal latestAccountBalance(Long userId, String accountType, int year) {
        List<SalaryAccountRecordEntity> records = loadRecords(userId, accountType).stream()
            .filter(record -> record.getRecordMonth() != null && record.getRecordMonth().getYear() <= year)
            .toList();
        if (records.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return defaultZero(records.get(records.size() - 1).getBalanceAfter());
    }

    private List<SalaryMonthRecordEntity> loadSalaryMonthRecords(Long userId, int year) {
        return salaryMonthRecordMapper.selectList(new LambdaQueryWrapper<SalaryMonthRecordEntity>()
                .eq(SalaryMonthRecordEntity::getUserId, userId)
                .ge(SalaryMonthRecordEntity::getSalaryMonth, LocalDate.of(year, 1, 1))
                .lt(SalaryMonthRecordEntity::getSalaryMonth, LocalDate.of(year + 1, 1, 1))
                .orderByAsc(SalaryMonthRecordEntity::getSalaryMonth)
                .orderByAsc(SalaryMonthRecordEntity::getUpdatedAt)
                .orderByAsc(SalaryMonthRecordEntity::getId))
            .stream()
            .sorted(Comparator
                .comparing(SalaryMonthRecordEntity::getSalaryMonth, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(SalaryMonthRecordEntity::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(SalaryMonthRecordEntity::getId, Comparator.nullsLast(Long::compareTo)))
            .toList();
    }

    private List<BigDecimal> resolveMonthlyPersonalDeductions(
        Long userId,
        SalaryProfileEntity profile,
        int year,
        int months,
        List<BigDecimal> monthlyGrossSalaries
    ) {
        Map<Integer, Map<String, SalaryAccountRecordEntity>> recordsByMonth = new HashMap<>();
        salaryAccountRecordMapper.selectList(new LambdaQueryWrapper<SalaryAccountRecordEntity>()
                .eq(SalaryAccountRecordEntity::getUserId, userId)
                .eq(SalaryAccountRecordEntity::getRecordType, RECORD_AUTO)
                .ge(SalaryAccountRecordEntity::getRecordMonth, LocalDate.of(year, 1, 1))
                .lt(SalaryAccountRecordEntity::getRecordMonth, LocalDate.of(year + 1, 1, 1)))
            .stream()
            .filter(record -> record.getRecordMonth() != null)
            .forEach(record -> recordsByMonth
                .computeIfAbsent(record.getRecordMonth().getMonthValue(), ignored -> new HashMap<>())
                .putIfAbsent(record.getAccountType(), record));

        List<BigDecimal> deductions = new ArrayList<>();
        for (int month = 1; month <= months; month++) {
            BigDecimal monthlyGrossSalary = monthlyGrossSalaries.get(month - 1);
            BigDecimal socialSecurityBase = resolveBase(profile.getSocialSecurityBase(), monthlyGrossSalary);
            BigDecimal housingFundBase = resolveBase(profile.getHousingFundBase(), monthlyGrossSalary);
            Map<String, SalaryAccountRecordEntity> monthRecords = recordsByMonth.getOrDefault(month, Map.of());

            BigDecimal housingFundPersonal = resolveRecordedPersonalAmount(
                monthRecords.get(ACCOUNT_HOUSING),
                roundedYuanRateAmount(housingFundBase, resolveRate(profile.getHousingFundPersonalRate(), DEFAULT_HOUSING_FUND_PERSONAL_RATE))
            );
            BigDecimal pensionPersonal = resolveRecordedPersonalAmount(
                monthRecords.get(ACCOUNT_SOCIAL),
                rateAmount(socialSecurityBase, resolveRate(profile.getPensionPersonalRate(), DEFAULT_PENSION_PERSONAL_RATE))
            );
            BigDecimal medicalPersonal = resolveRecordedPersonalAmount(
                monthRecords.get(ACCOUNT_MEDICAL),
                rateAmount(socialSecurityBase, resolveRate(profile.getMedicalPersonalRate(), DEFAULT_MEDICAL_PERSONAL_RATE))
            );
            BigDecimal unemploymentPersonal = rateAmount(
                socialSecurityBase,
                resolveRate(profile.getUnemploymentPersonalRate(), DEFAULT_UNEMPLOYMENT_PERSONAL_RATE)
            );

            deductions.add(housingFundPersonal
                .add(pensionPersonal)
                .add(medicalPersonal)
                .add(resolveMedicalFixedAmount(profile.getMedicalFixedAmount()))
                .add(unemploymentPersonal)
                .setScale(2, RoundingMode.HALF_UP));
        }
        return deductions;
    }

    private BigDecimal resolveRecordedPersonalAmount(SalaryAccountRecordEntity record, BigDecimal fallback) {
        if (record == null) {
            return defaultZero(fallback);
        }
        if (isDeletedAutoRecord(record)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return defaultZero(record.getPersonalAmount());
    }

    private BigDecimal monthlyPersonal(String accountType, SalaryComputation computation) {
        return switch (accountType) {
            case ACCOUNT_HOUSING -> computation.housingFundPersonal;
            case ACCOUNT_MEDICAL -> computation.medicalPersonal;
            default -> computation.pensionPersonal.setScale(2, RoundingMode.HALF_UP);
        };
    }

    private BigDecimal monthlyCompany(String accountType, SalaryComputation computation) {
        return switch (accountType) {
            case ACCOUNT_HOUSING -> computation.housingFundCompany;
            case ACCOUNT_MEDICAL -> computation.medicalCompany;
            default -> computation.pensionCompany.setScale(2, RoundingMode.HALF_UP);
        };
    }

    private BigDecimal autoRecordAmount(String accountType, BigDecimal personal, BigDecimal company) {
        if (ACCOUNT_MEDICAL.equals(accountType)) {
            return defaultZero(personal).setScale(2, RoundingMode.HALF_UP);
        }
        if (ACCOUNT_SOCIAL.equals(accountType)) {
            return defaultZero(personal).setScale(2, RoundingMode.HALF_UP);
        }
        return defaultZero(personal).add(defaultZero(company)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rateAmount(BigDecimal base, BigDecimal rate) {
        return defaultZero(base)
            .multiply(defaultZero(rate))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal roundedYuanRateAmount(BigDecimal base, BigDecimal rate) {
        return defaultZero(base)
            .multiply(defaultZero(rate))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveBase(BigDecimal base, BigDecimal monthlyGrossSalary) {
        BigDecimal normalizedBase = scale(base);
        if (normalizedBase.compareTo(BigDecimal.ZERO) > 0) {
            return normalizedBase;
        }
        return scale(monthlyGrossSalary);
    }

    private BigDecimal resolveRate(BigDecimal rate, BigDecimal fallback) {
        BigDecimal normalizedRate = scaleRate(rate);
        if (normalizedRate.compareTo(BigDecimal.ZERO) > 0) {
            return normalizedRate;
        }
        return scaleRate(fallback);
    }

    private BigDecimal resolveMedicalFixedAmount(BigDecimal amount) {
        BigDecimal normalizedAmount = scale(amount);
        if (normalizedAmount.compareTo(BigDecimal.ZERO) > 0) {
            return normalizedAmount;
        }
        return DEFAULT_MEDICAL_FIXED_AMOUNT;
    }

    private String normalizeAccountType(String accountType) {
        if ("social-security".equals(accountType)) {
            return ACCOUNT_SOCIAL;
        }
        if ("housing-fund".equals(accountType)) {
            return ACCOUNT_HOUSING;
        }
        if ("medical".equals(accountType)) {
            return ACCOUNT_MEDICAL;
        }
        if (ACCOUNT_TYPES.contains(accountType)) {
            return accountType;
        }
        throw new IllegalArgumentException("不支持的工资账户类型");
    }

    private String accountTitle(String accountType) {
        return switch (accountType) {
            case ACCOUNT_HOUSING -> "公积金账户";
            case ACCOUNT_MEDICAL -> "医保账户";
            default -> "社保账户";
        };
    }

    private String accountSubtitle(String accountType) {
        return switch (accountType) {
            case ACCOUNT_HOUSING -> "住房储备与月度双边缴存";
            case ACCOUNT_MEDICAL -> "个人医保个账与统筹划入";
            default -> "养老金个人账户累计";
        };
    }

    private String accountBadge(String accountType) {
        return switch (accountType) {
            case ACCOUNT_HOUSING -> "双边 12%";
            case ACCOUNT_MEDICAL -> "医保 + 个账";
            default -> "个人账户";
        };
    }

    private String resolveRecordPill(String recordType) {
        if (RECORD_INITIAL.equals(recordType)) {
            return "初始值";
        }
        if (RECORD_AUTO.equals(recordType)) {
            return "自动入账";
        }
        return "手动新增";
    }

    private String resolveManualNote(SalaryAccountRecordRequest request) {
        String note = StringUtils.hasText(request.getNote()) ? request.getNote().trim() : "";
        String impact = StringUtils.hasText(request.getImpactMode()) ? " · " + request.getImpactMode().trim() : "";
        if (note.isEmpty()) {
            return "手动维护记录" + impact;
        }
        return note + impact;
    }

    private String resolveRecordUpdateNote(SalaryAccountRecordEntity entity, SalaryAccountRecordRequest request) {
        if (RECORD_AUTO.equals(entity.getRecordType())) {
            return StringUtils.hasText(request.getNote()) ? request.getNote().trim() : entity.getNote();
        }
        return resolveManualNote(request);
    }

    private String resolveSalaryMonthNote(String note) {
        return StringUtils.hasText(note) ? note.trim() : null;
    }

    private BigDecimal recordNetAmount(SalaryAccountRecordEntity record) {
        return defaultZero(record.getAmount()).setScale(2, RoundingMode.HALF_UP);
    }

    private int resolveYear(String month) {
        if (!StringUtils.hasText(month)) {
            return LocalDate.now(SHANGHAI_ZONE).getYear();
        }
        return YearMonth.parse(month).getYear();
    }

    private int resolvePaidMonths(String month, int year, Integer payDay) {
        if (StringUtils.hasText(month)) {
            return YearMonth.parse(month).getMonthValue();
        }
        return resolveCurrentPaidMonths(year, payDay);
    }

    private int resolveOverviewMonth(String month, int year) {
        if (StringUtils.hasText(month)) {
            return YearMonth.parse(month).getMonthValue();
        }
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        if (year < today.getYear()) {
            return 12;
        }
        if (year > today.getYear()) {
            return 1;
        }
        return today.getMonthValue();
    }

    private int resolveCurrentPaidMonths(int year, Integer payDay) {
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        if (year < today.getYear()) {
            return 12;
        }
        if (year > today.getYear()) {
            return 0;
        }
        int currentMonth = today.getMonthValue();
        int resolvedPayDay = payDay == null ? 15 : Math.max(1, Math.min(payDay, 31));
        if (today.getDayOfMonth() < resolvedPayDay) {
            return Math.max(currentMonth - 1, 0);
        }
        return currentMonth;
    }

    private List<BigDecimal> resolveMonthlyGrossSalaries(Long userId, int year, int months, BigDecimal defaultMonthlyGrossSalary) {
        Map<Integer, BigDecimal> recordMap = salaryMonthRecordMapper.selectList(new LambdaQueryWrapper<SalaryMonthRecordEntity>()
                .eq(SalaryMonthRecordEntity::getUserId, userId)
                .ge(SalaryMonthRecordEntity::getSalaryMonth, LocalDate.of(year, 1, 1))
                .lt(SalaryMonthRecordEntity::getSalaryMonth, LocalDate.of(year + 1, 1, 1)))
            .stream()
            .filter(record -> record.getSalaryMonth() != null)
            .collect(Collectors.toMap(
                record -> record.getSalaryMonth().getMonthValue(),
                record -> scale(record.getGrossSalary()),
                (left, right) -> right
            ));

        List<BigDecimal> salaries = new ArrayList<>();
        for (int month = 1; month <= months; month++) {
            salaries.add(recordMap.getOrDefault(month, defaultMonthlyGrossSalary));
        }
        return salaries;
    }

    private BigDecimal sumMonthlyGrossSalaries(List<BigDecimal> monthlyGrossSalaries, int months) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int end = Math.min(Math.max(months, 0), monthlyGrossSalaries.size());
        for (int index = 0; index < end; index++) {
            total = total.add(defaultZero(monthlyGrossSalaries.get(index))).setScale(2, RoundingMode.HALF_UP);
        }
        return total;
    }

    private String resolveMonthValue(String month, Integer payDay) {
        if (StringUtils.hasText(month)) {
            return month;
        }
        return MONTH_KEY_FORMATTER.format(YearMonth.from(LocalDate.now(SHANGHAI_ZONE)));
    }

    private LocalDate normalizeMonth(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("发生月份不能为空");
        }
        return value.withDayOfMonth(1);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return defaultZero(value);
    }

    private BigDecimal scaleRate(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP) : value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePercent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatCurrency(BigDecimal value) {
        return "¥" + scale(value).toPlainString();
    }

    private String monthKey(int year, int month) {
        return String.format("%d-%02d", year, month);
    }

    private static final class SalaryComputation {
        private int paidMonths;
        private BigDecimal grossMonthlyIncome = BigDecimal.ZERO;
        private BigDecimal housingFundPersonal = BigDecimal.ZERO;
        private BigDecimal housingFundCompany = BigDecimal.ZERO;
        private BigDecimal pensionPersonal = BigDecimal.ZERO;
        private BigDecimal pensionCompany = BigDecimal.ZERO;
        private BigDecimal medicalPersonal = BigDecimal.ZERO;
        private BigDecimal medicalCompany = BigDecimal.ZERO;
        private BigDecimal medicalFixedAmount = BigDecimal.ZERO;
        private BigDecimal unemploymentPersonal = BigDecimal.ZERO;
        private BigDecimal unemploymentCompany = BigDecimal.ZERO;
        private BigDecimal monthlySpecialDeductionTotal = BigDecimal.ZERO;
        private BigDecimal personalDeductionMonthly = BigDecimal.ZERO;
        private BigDecimal annualIncome = BigDecimal.ZERO;
        private BigDecimal annualTax = BigDecimal.ZERO;
        private BigDecimal currentMonthTax = BigDecimal.ZERO;
        private BigDecimal currentMonthTakeHome = BigDecimal.ZERO;
        private BigDecimal annualNetIncome = BigDecimal.ZERO;
        private BigDecimal netRate = BigDecimal.ZERO;
        private BigDecimal socialAccountMonthlyIncrease = BigDecimal.ZERO;
        private BigDecimal housingFundMonthlyIncrease = BigDecimal.ZERO;
        private BigDecimal medicalMonthlyIncrease = BigDecimal.ZERO;
    }
}
