package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.app.controller.AccountController;
import com.example.app.mapper.*;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountReadServiceTest {
    private AccountService service(AccountingDatabase db, AccountMapper accounts, AccountTypeMapper types, GoldPriceService gold) {
        return new AccountService(accounts, types, db.mapper(DebtRecordMapper.class), mock(LiabilityRecordMapper.class),
            db.mapper(HumanRelationRecordMapper.class), db.mapper(InvestmentAutoInvestPlanMapper.class),
            db.mapper(InvestmentPositionMapper.class), db.mapper(InvestmentTransactionMapper.class),
            db.mapper(InvestmentDividendRecordMapper.class), db.mapper(TransactionMapper.class),
            db.mapper(RenewalSubscriptionMapper.class), gold, db.jdbc, mock(ToolServiceClient.class), self -> Set.of(self, 2L),
            db.mapper(SavingsGoalMapper.class));
    }

    @Test void accountHttpReadsRejectBlankScopesAndPreserveSelfAndFamilyIsolation() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,status) VALUES(4,2,1,'家人',100,'active'),(5,3,1,'外人',500,'active')");
            var accounts = db.mapper(AccountMapper.class);
            var service = service(db, accounts, db.mapper(AccountTypeMapper.class), mock(GoldPriceService.class));
            assertThrows(IllegalArgumentException.class, () -> service.list(null, null, null));
            assertThrows(IllegalArgumentException.class, () -> service.summary(null));
            var json = new ObjectMapper();
            Filter identity = (request, response, chain) -> {
                request.setAttribute(SecurityUtils.USER_ID_ATTRIBUTE, 1L);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("self", null, List.of()));
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes((jakarta.servlet.http.HttpServletRequest) request));
                try { chain.doFilter(request, response); }
                finally { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }
            };
            var scope = new UserIdentityScopeFilter(json, self -> Set.of(self, 2L), new SecurityErrorWriter(json));
            var mvc = MockMvcBuilders.standaloneSetup(new AccountController(service, null, null, null))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(identity, scope).build();
            String url = "/api/finance/accounts";
            mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].userId").value(1));
            mvc.perform(get(url).param("userId", "2")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
            mvc.perform(get(url).param("userId", "")).andExpect(status().isBadRequest());
            mvc.perform(get(url + "/summary").param("userId", " ")).andExpect(status().isBadRequest());
            mvc.perform(get(url + "/summary").param("userId", "3")).andExpect(status().isForbidden());
            mvc.perform(get(url + "/summary").param("userId", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accounts[0].userId").value(2));
        }
    }

    @Test void summaryReusesBalancesAndBatchesTermPrincipalWhileKeepingNetWorthRules() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("UPDATE accounts SET include_in_net_worth=TRUE");
            db.jdbc.update("INSERT INTO account_types(id,code,balance_direction) VALUES(4,'term_deposit','debit'),(5,'credit_card','credit'),(6,'debt','credit')");
            db.jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,status,include_in_net_worth) VALUES(4,1,4,'定存一',0,'active',TRUE),(5,1,4,'定存二',0,'active',TRUE),(6,1,4,'空定存',0,'active',TRUE),(7,1,5,'负债',100,'active',TRUE),(8,1,1,'不计资产',900,'active',FALSE),(9,1,6,'往来',0,'active',TRUE)");
            db.jdbc.update("INSERT INTO term_deposits(id,user_id,account_id,principal,status) VALUES(1,1,4,100,'active'),(2,1,4,200,'active'),(3,1,4,999,'settled'),(4,1,5,400,'active')");
            db.jdbc.update("INSERT INTO debt_records(id,user_id,account_id,direction,record_type,amount,status) VALUES(1,1,9,'receivable','borrow',50,'active')");
            // 真实 SQL 和 Mapper；代理仅统计调用次数。
            var accounts = mock(AccountMapper.class, delegatesTo(db.mapper(AccountMapper.class)));
            var types = mock(AccountTypeMapper.class, delegatesTo(db.mapper(AccountTypeMapper.class)));
            var gold = mock(GoldPriceService.class);
            var service = service(db, accounts, types, gold);
            var result = service.summary(1L);
            assertEquals(0, new BigDecimal("2150").compareTo(result.totalAssets()));
            assertEquals(9, result.accounts().size());
            assertEquals(0, result.accounts().stream().filter(a -> a.getId() == 6).findFirst().orElseThrow().getCurrentBalance().signum());
            verify(accounts, times(1)).selectList(any(Wrapper.class));
            verify(types, times(1)).selectByIds(anyCollection());
            verify(accounts, times(1)).termDepositPrincipals(anyList());
            verify(accounts, never()).termDepositPrincipal(anyLong());
            verifyNoInteractions(gold);
            assertEquals(0, result.totalAssets().compareTo(service.overview(1L).getTotalAssets()));
        }
    }

    @Test void investmentOnlyAccountsDoNotNeedGoldAndGoldReadFailureUsesStoredValue() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("UPDATE accounts SET include_in_net_worth=TRUE");
            var gold = mock(GoldPriceService.class);
            when(gold.getCachedSpotPrice()).thenThrow(new IllegalStateException("market unavailable"));
            var service = service(db, db.mapper(AccountMapper.class), db.mapper(AccountTypeMapper.class), gold);
            assertEquals(3, service.list(1L, null, "active").size());
            verifyNoInteractions(gold);
            db.jdbc.update("INSERT INTO account_types(id,code) VALUES(4,'gold')");
            db.jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,status,include_in_net_worth) VALUES(4,1,4,'黄金',0,'active',TRUE)");
            db.jdbc.update("INSERT INTO investment_positions(id,user_id,account_id,holding_quantity,market_value,status) VALUES(1,1,4,2,1200,'active')");
            assertEquals(0, new BigDecimal("2700").compareTo(service.summary(1L).totalAssets()));
            verify(gold, times(1)).getCachedSpotPrice();
            doReturn(new BigDecimal("650")).when(gold).getCachedSpotPrice();
            assertEquals(0, new BigDecimal("2800").compareTo(service.summary(1L).totalAssets()));
        }
    }
}
