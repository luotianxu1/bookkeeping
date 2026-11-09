package com.example.app.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.dto.*;
import com.example.app.entity.*;
import com.example.app.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CategoryBudgetService {
    private final CategoryBudgetMapper budgets;
    private final CategoryMapper categories;
    private final AccountMapper accounts;
    private final TransactionService transactions;
    public CategoryBudgetService(CategoryBudgetMapper budgets, CategoryMapper categories, AccountMapper accounts, TransactionService transactions) {
        this.budgets = budgets; this.categories = categories; this.accounts = accounts; this.transactions = transactions;
    }
    public List<CategoryBudgetResponse> list(long userId, LocalDate month) {
        month = month.withDayOfMonth(1);
        var rows = budgets.selectList(new LambdaQueryWrapper<CategoryBudgetEntity>().eq(CategoryBudgetEntity::getUserId, userId)
            .eq(CategoryBudgetEntity::getBudgetMonth, month).orderByAsc(CategoryBudgetEntity::getId));
        if (rows.isEmpty()) return List.of();
        var used = transactions.listMergedCashTransactions(List.of(userId), month, month.plusMonths(1).minusDays(1)).stream()
            .filter(t -> "expense".equals(t.getType()) && t.getCategoryId() != null && "CNY".equals(t.getCurrencyCode()))
            .collect(Collectors.groupingBy(TransactionResponse::getCategoryId,
                Collectors.reducing(BigDecimal.ZERO, TransactionResponse::getAmount, BigDecimal::add)));
        var ids = rows.stream().map(CategoryBudgetEntity::getCategoryId).collect(Collectors.toSet());
        var names = categories.selectByIds(ids).stream().collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getName));
        return rows.stream().map(b -> {
            var spent = used.getOrDefault(b.getCategoryId(), BigDecimal.ZERO);
            return new CategoryBudgetResponse(b.getId(), b.getBudgetMonth(), b.getCategoryId(), names.getOrDefault(b.getCategoryId(), "已删除分类"),
                b.getAmount(), spent, b.getAmount().subtract(spent), spent.multiply(BigDecimal.valueOf(100)).divide(b.getAmount(), 2, RoundingMode.HALF_UP), b.getCurrencyCode());
        }).toList();
    }
    @Transactional
    public void save(long userId, CategoryBudgetRequest request) {
        accounts.lockUserForWrite(userId);
        var category = categories.selectById(request.categoryId());
        if (category == null || (category.getUserId() != null && !Objects.equals(category.getUserId(), userId))
            || !"expense".equals(category.getType()) || !"active".equals(category.getStatus())) throw new IllegalArgumentException("请选择有效支出分类");
        if (categories.countActiveChildrenForUser(category.getId(), userId) > 0) throw new IllegalArgumentException("请选择具体的二级分类，避免预算重复统计");
        if (request.amount() == null || request.amount().signum() <= 0 || request.amount().scale() > 2)
            throw new IllegalArgumentException("请输入大于0且最多两位小数的预算金额");
        var month = request.budgetMonth().withDayOfMonth(1);
        var entity = budgets.selectOne(new LambdaQueryWrapper<CategoryBudgetEntity>().eq(CategoryBudgetEntity::getUserId, userId)
            .eq(CategoryBudgetEntity::getBudgetMonth, month).eq(CategoryBudgetEntity::getCategoryId, category.getId()));
        boolean create = entity == null;
        if (create) entity = new CategoryBudgetEntity();
        entity.setUserId(userId); entity.setCategoryId(category.getId()); entity.setBudgetMonth(month);
        entity.setAmount(request.amount()); entity.setCurrencyCode("CNY");
        if (create) budgets.insert(entity); else budgets.updateById(entity);
    }
    @Transactional
    public void delete(long userId, long id) {
        accounts.lockUserForWrite(userId);
        if (budgets.delete(new LambdaQueryWrapper<CategoryBudgetEntity>().eq(CategoryBudgetEntity::getId, id)
            .eq(CategoryBudgetEntity::getUserId, userId)) == 0) throw new IllegalArgumentException("分类预算不存在");
    }
}
