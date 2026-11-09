package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.dto.TermDepositDtos.*;
import com.example.app.entity.*;
import com.example.app.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 定期本金由存续存单汇总；关联流水和现金变化在同一用户账务事务内提交。 */
@Service
public class TermDepositService {
    private static final BigDecimal MAX = new BigDecimal("9999999999999999.99");
    private static final Set<String> ACTIONS = Set.of("withdraw", "renew_principal", "renew_all");
    private final TermDepositMapper deposits;
    private final TermDepositOperationMapper operations;
    private final AccountMapper accounts;
    private final AccountTypeMapper types;
    private final TransactionMapper transactions;
    private final CategoryMapper categories;
    private final SavingsAllocationService savings;
    private final AccountingIdempotencyService idempotency;
    private final Clock clock;

    @Autowired
    public TermDepositService(TermDepositMapper deposits, TermDepositOperationMapper operations, AccountMapper accounts,
        AccountTypeMapper types, TransactionMapper transactions, CategoryMapper categories,
        SavingsAllocationService savings, AccountingIdempotencyService idempotency) {
        this(deposits, operations, accounts, types, transactions, categories, savings, idempotency, Clock.system(ZoneId.of("Asia/Shanghai")));
    }
    TermDepositService(TermDepositMapper deposits, TermDepositOperationMapper operations, AccountMapper accounts,
        AccountTypeMapper types, TransactionMapper transactions, CategoryMapper categories,
        SavingsAllocationService savings, AccountingIdempotencyService idempotency, Clock clock) {
        this.deposits=deposits; this.operations=operations; this.accounts=accounts; this.types=types;
        this.transactions=transactions; this.categories=categories; this.savings=savings; this.idempotency=idempotency;
        this.clock=clock.withZone(ZoneId.of("Asia/Shanghai"));
    }
    private LocalDate today() { return LocalDate.now(clock); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }

    @Transactional(readOnly=true)
    public List<View> list(long userId, Long accountId) {
        return deposits.selectList(new LambdaQueryWrapper<TermDepositEntity>()
            .eq(TermDepositEntity::getUserId,userId).ne(TermDepositEntity::getStatus,"voided")
            .eq(accountId!=null,TermDepositEntity::getAccountId,accountId)
            .orderByAsc(TermDepositEntity::getMaturityDate).orderByDesc(TermDepositEntity::getId)).stream()
            .sorted(Comparator.comparingInt(d -> "active".equals(d.getStatus()) ? 0 : 1)).map(this::view).toList();
    }

    @Transactional(readOnly=true)
    public Detail detail(long userId, long id) {
        var deposit=requireDeposit(userId,id);
        var history=operations.selectList(new LambdaQueryWrapper<TermDepositOperationEntity>()
            .eq(TermDepositOperationEntity::getDepositId,id).orderByDesc(TermDepositOperationEntity::getId)).stream()
            .map(o -> {
                var income=o.getInterestTransactionId()==null?null:transactions.selectById(o.getInterestTransactionId());
                Long categoryId=income==null?null:income.getCategoryId();
                return new Operation(o.getId(),o.getOperation(),o.getCashAccountId(),accountName(o.getCashAccountId()),o.getChildDepositId(),
                    categoryId,categoryName(categoryId),o.getPrincipal(),o.getInterest(),o.getCashDelta(),o.getOccurredOn(),o.getDetail(),o.getReversedAt(),o.getCreatedAt());
            }).toList();
        return new Detail(view(deposit),history);
    }

    @Transactional
    public View create(long userId, Create request) {
        accounts.lockUserForWrite(userId);
        return idempotency.execute(userId,"term-deposit-create",request.requestId(),request,View.class,()->{
            validateTerms(request.name(),request.principal(),request.annualRate(),request.startDate(),request.maturityDate(),request.termMonths(),request.maturityAction(),request.remark());
            if (!Set.of("new","existing").contains(Objects.toString(request.openingMode(),""))) fail("请选择现在存入或补录已有存单");
            var account=requireAccount(userId,request.accountId(),"term_deposit",null);
            var payout=requireAccount(userId,request.payoutAccountId(),"cash",account.getCurrencyCode());
            if(request.interestCategoryId()!=null) requireIncomeCategory(userId,request.interestCategoryId());
            var funding="new".equals(request.openingMode()) ? requireAccount(userId,request.fundingAccountId(),"cash",account.getCurrencyCode()) : null;
            if(funding==null && request.savingsAllocations()!=null && !request.savingsAllocations().isEmpty()) fail("补录存单不能扣减储蓄目标");
            var d=new TermDepositEntity(); d.setUserId(userId); d.setAccountId(account.getId()); d.setPayoutAccountId(payout.getId());
            d.setFundingAccountId(funding==null?null:funding.getId()); d.setCurrencyCode(account.getCurrencyCode());
            d.setInterestCategoryId(request.interestCategoryId());
            d.setPrincipal(request.principal()); d.setOpeningMode(request.openingMode()); d.setStatus("active");
            fillTerms(d,request.name(),request.annualRate(),request.startDate(),request.maturityDate(),request.termMonths(),request.maturityAction(),request.remark());
            d.setCreatedAt(now()); deposits.insert(d);
            var op=operation(d,funding==null?"opening":"deposit",request.startDate(),request.remark());
            if(funding!=null) {
                var transfer=transfer(d,funding.getId(),account.getId(),d.getPrincipal(),request.startDate(),"存入定期");
                savings.replace(userId,transfer.getId(),transfer,request.savingsAllocations());
                debit(funding.getId(),d.getPrincipal());
                op.setCashAccountId(funding.getId()); op.setCashDelta(d.getPrincipal().negate()); op.setPrincipalTransactionId(transfer.getId());
            }
            operations.insert(op); checkPrincipal(account.getId()); return view(d);
        });
    }

    private record Command(long depositId,Object request) {}
    @Transactional
    public View edit(long userId,long id,Edit request) {
        accounts.lockUserForWrite(userId);
        return idempotency.execute(userId,"term-deposit-edit",request.requestId(),new Command(id,request),View.class,()->{
            var d=requireDeposit(userId,id); if("voided".equals(d.getStatus())) fail("已撤销存单不能修改");
            validateTerms(request.name(),d.getPrincipal(),request.annualRate(),request.startDate(),request.maturityDate(),request.termMonths(),request.maturityAction(),request.remark());
            requireAccount(userId,request.payoutAccountId(),"cash",d.getCurrencyCode());
            if(request.interestCategoryId()!=null) requireIncomeCategory(userId,request.interestCategoryId());
            if(!Objects.equals(d.getStartDate(),request.startDate())) fail("起息日更正请先撤销存单后重新登记");
            if(!"active".equals(d.getStatus()) && (d.getAnnualRate().compareTo(request.annualRate())!=0
                || !Objects.equals(d.getMaturityDate(),request.maturityDate()) || !Objects.equals(d.getTermMonths(),request.termMonths())))
                fail("已结清存单更正利率或存期前，请先撤销结算");
            String before=termsText(d);
            d.setPayoutAccountId(request.payoutAccountId());
            d.setInterestCategoryId(request.interestCategoryId());
            fillTerms(d,request.name(),request.annualRate(),request.startDate(),request.maturityDate(),request.termMonths(),request.maturityAction(),request.remark());
            deposits.updateById(d);
            operations.insert(operation(d,"edit",today(),before+" → "+termsText(d)));
            return view(d);
        });
    }

    @Transactional
    public View settle(long userId,long id,Settle request) {
        accounts.lockUserForWrite(userId);
        return idempotency.execute(userId,"term-deposit-settle",request.requestId(),new Command(id,request),View.class,()->{
            var d=requireActive(userId,id);
            if(!ACTIONS.contains(Objects.toString(request.action(),""))) fail("请选择取回、本金续存或本息续存");
            validDate(request.occurredOn());
            if(request.occurredOn().isBefore(d.getStartDate()) || request.occurredOn().isAfter(today())) fail("到账日期须在起息日至今天之间");
            money(request.actualInterest(),true); remark(request.remark());
            var cash=requireAccount(userId,request.payoutAccountId(),"cash",d.getCurrencyCode());
            requireAccount(userId,d.getAccountId(),"term_deposit",d.getCurrencyCode());
            boolean renewal=!"withdraw".equals(request.action());
            BigDecimal principal=d.getPrincipal(), interest=request.actualInterest();
            if(principal.add(interest).compareTo(MAX)>0) fail("本息合计超出金额范围");
            if(renewal && request.occurredOn().isBefore(d.getMaturityDate())) fail("存单到期后才能续存，提前取回请选择支取");
            if(interest.signum()>0 || request.interestCategoryId()!=null) requireIncomeCategory(userId,request.interestCategoryId());
            var op=operation(d,renewal?request.action():request.occurredOn().isBefore(d.getMaturityDate())?"early_withdraw":"withdraw",request.occurredOn(),request.remark());
            op.setCashAccountId(cash.getId()); op.setInterest(interest);
            op.setPrincipalTransactionId(transfer(d,d.getAccountId(),cash.getId(),principal,request.occurredOn(),"定期本金取回").getId());
            if(interest.signum()>0) op.setInterestTransactionId(income(d,cash.getId(),request.interestCategoryId(),interest,request.occurredOn()).getId());
            BigDecimal cashDelta=principal.add(interest);
            if(renewal) {
                BigDecimal nextPrincipal="renew_all".equals(request.action()) ? principal.add(interest) : principal;
                validateTerms(d.getName(),nextPrincipal,request.renewalAnnualRate(),request.occurredOn(),request.renewalMaturityDate(),request.renewalTermMonths(),d.getMaturityAction(),d.getRemark());
                var child=new TermDepositEntity(); child.setUserId(userId); child.setAccountId(d.getAccountId()); child.setFundingAccountId(cash.getId());
                child.setPayoutAccountId(cash.getId()); child.setParentDepositId(d.getId()); child.setCurrencyCode(d.getCurrencyCode());
                child.setInterestCategoryId(request.interestCategoryId());
                child.setPrincipal(nextPrincipal); child.setOpeningMode("renewal"); child.setStatus("active"); child.setCreatedAt(now());
                fillTerms(child,d.getName(),request.renewalAnnualRate(),request.occurredOn(),request.renewalMaturityDate(),request.renewalTermMonths(),d.getMaturityAction(),d.getRemark());
                deposits.insert(child); op.setChildDepositId(child.getId());
                op.setRenewalTransactionId(transfer(child,cash.getId(),d.getAccountId(),nextPrincipal,request.occurredOn(),"定期续存").getId());
                operations.insert(operation(child,"renewal_open",request.occurredOn(),"由存单 #"+d.getId()+" 续存；撤销请在原存单操作"));
                cashDelta=cashDelta.subtract(nextPrincipal);
            }
            credit(cash.getId(),cashDelta); op.setCashDelta(cashDelta); operations.insert(op);
            d.setStatus("closed"); d.setUpdatedAt(now()); deposits.updateById(d); checkPrincipal(d.getAccountId()); return view(d);
        });
    }

    @Transactional
    public View reverse(long userId,long id,Reverse request) {
        accounts.lockUserForWrite(userId);
        return idempotency.execute(userId,"term-deposit-reverse",request.requestId(),new Command(id,request),View.class,()->{
            var d=requireDeposit(userId,id);
            var op=request.operationId()==null?null:operations.selectById(request.operationId());
            if(!"closed".equals(d.getStatus()) || op==null || !Objects.equals(op.getDepositId(),id)
                || !Objects.equals(op.getUserId(),userId) || op.getReversedAt()!=null
                || !Set.of("withdraw","early_withdraw","renew_all","renew_principal").contains(op.getOperation())) fail("这笔结算不能撤销，请刷新后重试");
            if(op.getChildDepositId()!=null) {
                var child=requireActive(userId,op.getChildDepositId());
                child.setStatus("voided"); child.setUpdatedAt(now()); deposits.updateById(child);
                operations.insert(operation(child,"cancel",today(),"撤销原存单 #"+id+" 的续存结算"));
            }
            requireAccount(userId,op.getCashAccountId(),"cash",d.getCurrencyCode());
            debit(op.getCashAccountId(),op.getCashDelta());
            voidTransaction(userId,op.getRenewalTransactionId()); voidTransaction(userId,op.getInterestTransactionId()); voidTransaction(userId,op.getPrincipalTransactionId());
            op.setReversedAt(now()); operations.updateById(op);
            d.setStatus("active"); d.setUpdatedAt(now()); deposits.updateById(d);
            operations.insert(operation(d,"reverse",today(),"撤销结算 #"+op.getId()));
            checkPrincipal(d.getAccountId()); return view(d);
        });
    }

    @Transactional
    public View cancel(long userId,long id,Cancel request) {
        accounts.lockUserForWrite(userId);
        return idempotency.execute(userId,"term-deposit-cancel",request.requestId(),new Command(id,request),View.class,()->{
            var d=requireActive(userId,id);
            if(d.getParentDepositId()!=null) fail("续存存单请在原存单中撤销续存结算");
            var opening=operations.selectOne(new LambdaQueryWrapper<TermDepositOperationEntity>()
                .eq(TermDepositOperationEntity::getDepositId,id).in(TermDepositOperationEntity::getOperation,List.of("opening","deposit")));
            if(opening==null || opening.getReversedAt()!=null) fail("存单已撤销");
            if(opening.getCashAccountId()!=null) {
                var cash=requireAccount(userId,opening.getCashAccountId(),"cash",d.getCurrencyCode());
                voidTransaction(userId,opening.getPrincipalTransactionId());
                credit(cash.getId(),d.getPrincipal());
                accounts.requireSavingsCovered(cash.getId(),accounts.selectById(cash.getId()).getCurrentBalance());
            }
            opening.setReversedAt(now()); operations.updateById(opening);
            d.setStatus("voided"); d.setUpdatedAt(now()); deposits.updateById(d);
            operations.insert(operation(d,"cancel",today(),"撤销存单；恢复原账户资金和关联目标分配")); return view(d);
        });
    }

    private void voidTransaction(long userId,Long id) {
        if(id==null)return;
        var t=transactions.selectById(id);
        if(t==null || !Objects.equals(t.getUserId(),userId) || !"normal".equals(t.getStatus())) fail("关联流水状态异常，无法撤销");
        savings.replace(userId,id,null,List.of()); t.setStatus("voided"); transactions.updateById(t);
    }
    private TermDepositEntity requireDeposit(long userId,long id) {
        var d=deposits.selectById(id);
        if(d==null || !Objects.equals(d.getUserId(),userId)) fail("存单不存在");
        return d;
    }
    private TermDepositEntity requireActive(long userId,long id) {
        var d=requireDeposit(userId,id); if(!"active".equals(d.getStatus()))fail("存单已结清或撤销，请刷新后重试"); return d;
    }
    private AccountEntity requireAccount(long userId,Long id,String code,String currency) {
        var a=id==null?null:accounts.selectById(id);
        if(a==null || !Objects.equals(a.getUserId(),userId) || !"active".equals(a.getStatus())) fail("请选择本人启用的账户");
        var type=types.selectById(a.getAccountTypeId());
        if(type==null || !code.equals(type.getCode())) fail("term_deposit".equals(code)?"请选择定期账户":"请选择现金账户");
        if(currency!=null && !currency.equals(a.getCurrencyCode())) fail("定期与现金账户币种必须一致");
        return a;
    }
    private void requireIncomeCategory(long userId,Long id) {
        var c=id==null?null:categories.selectById(id);
        if(c==null || (c.getUserId()!=null && !Objects.equals(c.getUserId(),userId)) || !"income".equals(c.getType()) || !"active".equals(c.getStatus())) fail("请选择启用的收入分类记录利息");
        if(categories.countActiveChildrenForUser(id,userId)>0) fail("请选择具体的二级收入分类记录利息");
    }
    private TransactionEntity transfer(TermDepositEntity d,Long from,Long to,BigDecimal amount,LocalDate date,String title) {
        var t=transaction(d,amount,date,title); t.setType("transfer"); t.setFromAccountId(from); t.setToAccountId(to); transactions.insert(t); return t;
    }
    private TransactionEntity income(TermDepositEntity d,Long cash,Long category,BigDecimal amount,LocalDate date) {
        var t=transaction(d,amount,date,"定期利息"); t.setType("income"); t.setAccountId(cash); t.setCategoryId(category); transactions.insert(t); return t;
    }
    private TransactionEntity transaction(TermDepositEntity d,BigDecimal amount,LocalDate date,String title) {
        var t=new TransactionEntity(); t.setUserId(d.getUserId()); t.setTransactionNo("TD-"+UUID.randomUUID());
        t.setAmount(amount); t.setCurrencyCode(d.getCurrencyCode()); t.setOccurredAt(date.atStartOfDay());
        t.setTitle(title+" · "+d.getName()); t.setRemark("定期存单 #"+d.getId()); t.setStatus("normal"); t.setReimbursementAmount(BigDecimal.ZERO); return t;
    }
    private TermDepositOperationEntity operation(TermDepositEntity d,String type,LocalDate date,String detail) {
        var o=new TermDepositOperationEntity(); o.setUserId(d.getUserId()); o.setDepositId(d.getId()); o.setOperation(type);
        o.setPrincipal(d.getPrincipal()); o.setInterest(BigDecimal.ZERO); o.setCashDelta(BigDecimal.ZERO); o.setOccurredOn(date); o.setDetail(detail); o.setCreatedAt(now()); return o;
    }
    private void fillTerms(TermDepositEntity d,String name,BigDecimal rate,LocalDate start,LocalDate end,Integer months,String action,String remark) {
        d.setName(name.trim()); d.setAnnualRate(rate); d.setStartDate(start); d.setMaturityDate(end); d.setTermMonths(months);
        d.setMaturityAction(action); d.setRemark(remark==null?"":remark.trim()); d.setUpdatedAt(now());
    }
    private void validateTerms(String name,BigDecimal principal,BigDecimal rate,LocalDate start,LocalDate end,Integer months,String action,String remark) {
        if(name==null || name.isBlank() || name.trim().length()>80)fail("请输入不超过80字的存单名称");
        money(principal,false);
        if(rate==null || rate.signum()<0 || rate.compareTo(new BigDecimal("100"))>0 || rate.stripTrailingZeros().scale()>4)fail("年利率须在0至100之间，最多四位小数");
        validDate(start); validDate(end);
        if(start.isAfter(today()) || !end.isAfter(start))fail("起息日不能晚于今天，到期日须晚于起息日");
        if(months!=null && (months<1 || months>1200))fail("存期须在1至1200个月之间");
        if(!ACTIONS.contains(Objects.toString(action,"")))fail("请选择有效的到期处理意向");
        remark(remark);
    }
    private void money(BigDecimal value,boolean zero) {
        if(value==null || value.signum()<(zero?0:1) || value.stripTrailingZeros().scale()>2 || value.compareTo(MAX)>0)fail("请输入有效金额，最多两位小数");
    }
    private void validDate(LocalDate date) { if(date==null || date.getYear()<1000 || date.getYear()>9999)fail("请选择有效日期"); }
    private void remark(String value) { if(value!=null && value.length()>500)fail("备注最多500字"); }
    private void debit(Long account,BigDecimal amount) { if(amount.signum()>0 && accounts.subtractBalanceIfSufficient(account,amount)!=1)fail("账户余额不足"); }
    private void credit(Long account,BigDecimal amount) {
        if(accounts.selectById(account).getCurrentBalance().add(amount).compareTo(MAX)>0)fail("账户余额超出金额范围");
        if(amount.signum()>0 && accounts.addBalance(account,amount)!=1)fail("账户不可用");
    }
    private void checkPrincipal(Long account) { if(accounts.termDepositPrincipal(account).compareTo(MAX)>0)fail("定期账户本金合计超出金额范围"); }
    static BigDecimal expectedInterest(TermDepositEntity d) {
        BigDecimal years=d.getTermMonths()!=null && d.getStartDate().plusMonths(d.getTermMonths()).equals(d.getMaturityDate())
            ? BigDecimal.valueOf(d.getTermMonths()).divide(BigDecimal.valueOf(12),16,RoundingMode.HALF_UP)
            : BigDecimal.valueOf(ChronoUnit.DAYS.between(d.getStartDate(),d.getMaturityDate())).divide(BigDecimal.valueOf(365),16,RoundingMode.HALF_UP);
        return d.getPrincipal().multiply(d.getAnnualRate()).multiply(years).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
    }
    private String accountName(Long id) { var a=id==null?null:accounts.selectById(id); return a==null?null:a.getName(); }
    private String categoryName(Long id) {
        var category=id==null?null:categories.selectById(id);
        if(category==null)return null;
        var parent=category.getParentId()==null?null:categories.selectById(category.getParentId());
        return parent==null?category.getName():parent.getName()+" / "+category.getName();
    }
    private String termsText(TermDepositEntity d) { return d.getName()+"，年利率 "+d.getAnnualRate()+"%，到期 "+d.getMaturityDate()+"，到账账户 #"+d.getPayoutAccountId()+"，利息分类 "+Objects.toString(categoryName(d.getInterestCategoryId()),"未设置")+"，到期意向 "+d.getMaturityAction()+"，备注："+Objects.toString(d.getRemark(),""); }
    private View view(TermDepositEntity d) {
        var interest=expectedInterest(d);
        return new View(d.getId(),d.getAccountId(),accountName(d.getAccountId()),d.getFundingAccountId(),d.getPayoutAccountId(),d.getInterestCategoryId(),categoryName(d.getInterestCategoryId()),d.getParentDepositId(),d.getName(),
            d.getCurrencyCode(),d.getPrincipal(),d.getAnnualRate(),d.getStartDate(),d.getMaturityDate(),d.getTermMonths(),d.getMaturityAction(),d.getOpeningMode(),
            "active".equals(d.getStatus()) && !d.getMaturityDate().isAfter(today())?"due":d.getStatus(),d.getRemark(),interest,d.getPrincipal().add(interest),
            ChronoUnit.DAYS.between(today(),d.getMaturityDate()),today(),"active".equals(d.getStatus()) && d.getParentDepositId()==null);
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
