package com.example.tool.service;

import com.example.tool.dto.PhotographyOrderRequest;
import com.example.tool.dto.PhotographyOrderResponse;
import com.example.tool.entity.AccountEntity;
import com.example.tool.entity.AccountTypeEntity;
import com.example.tool.entity.CategoryEntity;
import com.example.tool.entity.PhotographyOrderEntity;
import com.example.tool.entity.TransactionEntity;
import com.example.tool.mapper.AccountMapper;
import com.example.tool.mapper.AccountTypeMapper;
import com.example.tool.mapper.CategoryMapper;
import com.example.tool.mapper.PhotographyOrderMapper;
import com.example.tool.mapper.TransactionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotographyOrderServiceTest {

    @Mock
    private PhotographyOrderMapper photographyOrderMapper;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountTypeMapper accountTypeMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private PhotographyOrderService photographyOrderService;

    @Test
    void createShouldUseCurrentTimeForDepositRecord() {
        PhotographyOrderRequest request = new PhotographyOrderRequest();
        LocalDateTime shootAt = LocalDateTime.of(2026, 6, 18, 14, 30);
        request.setUserId(1L);
        request.setOrderType("wedding");
        request.setShootAt(shootAt);
        request.setTotalAmount(new BigDecimal("1000.00"));
        request.setDepositAmount(new BigDecimal("200.00"));
        request.setFinalAmount(new BigDecimal("800.00"));
        request.setDepositAccountId(11L);
        request.setAddress("测试地址");
        request.setRemark("测试备注");

        AccountEntity account = new AccountEntity();
        account.setId(11L);
        account.setUserId(1L);
        account.setAccountTypeId(21L);
        account.setStatus("active");
        account.setCurrentBalance(new BigDecimal("500.00"));

        AccountTypeEntity accountType = new AccountTypeEntity();
        accountType.setId(21L);
        accountType.setCode("cash");

        CategoryEntity category = new CategoryEntity();
        category.setId(31L);
        category.setUserId(1L);
        category.setType("income");
        category.setName("摄影收入");
        category.setStatus("active");

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        ArgumentCaptor<PhotographyOrderEntity> orderCaptor = ArgumentCaptor.forClass(PhotographyOrderEntity.class);

        when(accountMapper.selectById(11L)).thenReturn(account);
        when(accountTypeMapper.selectById(21L)).thenReturn(accountType);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(accountMapper.selectByIds(any())).thenReturn(List.of(account));

        doAnswer(invocation -> {
            TransactionEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        }).when(transactionMapper).insert(transactionCaptor.capture());

        doAnswer(invocation -> {
            PhotographyOrderEntity entity = invocation.getArgument(0);
            entity.setId(201L);
            return 1;
        }).when(photographyOrderMapper).insert(orderCaptor.capture());

        doAnswer(invocation -> orderCaptor.getValue()).when(photographyOrderMapper).selectById(201L);

        LocalDateTime beforeCreate = LocalDateTime.now();
        PhotographyOrderResponse response = photographyOrderService.create(request);
        LocalDateTime afterCreate = LocalDateTime.now();

        assertThat(transactionCaptor.getValue().getOccurredAt()).isBetween(beforeCreate, afterCreate);
        assertThat(transactionCaptor.getValue().getOccurredAt()).isNotEqualTo(shootAt);
        assertThat(orderCaptor.getValue().getDepositReceivedAt()).isEqualTo(transactionCaptor.getValue().getOccurredAt());
        assertThat(response.getDepositReceivedAt()).isEqualTo(transactionCaptor.getValue().getOccurredAt());
        assertThat(response.getShootAt()).isEqualTo(shootAt);
    }

    @Test
    void cancelShouldKeepDepositAndVoidFinalPayment() {
        PhotographyOrderEntity order = new PhotographyOrderEntity();
        order.setId(201L);
        order.setUserId(1L);
        order.setOrderNo("PHOTO202606180001");
        order.setOrderType("wedding");
        order.setStatus("pending");
        order.setShootAt(LocalDateTime.of(2026, 6, 18, 14, 30));
        order.setTotalAmount(new BigDecimal("1000.00"));
        order.setDepositAmount(new BigDecimal("200.00"));
        order.setFinalAmount(new BigDecimal("800.00"));
        order.setDepositAccountId(11L);
        order.setDepositTransactionId(101L);
        order.setDepositReceivedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        order.setFinalAccountId(11L);
        order.setFinalTransactionId(102L);
        order.setFinalReceivedAt(LocalDateTime.of(2026, 6, 18, 16, 0));

        AccountEntity account = new AccountEntity();
        account.setId(11L);
        account.setUserId(1L);
        account.setCurrentBalance(new BigDecimal("1000.00"));

        TransactionEntity finalTransaction = new TransactionEntity();
        finalTransaction.setId(102L);
        finalTransaction.setUserId(1L);
        finalTransaction.setAccountId(11L);
        finalTransaction.setAmount(new BigDecimal("800.00"));
        finalTransaction.setStatus("normal");

        when(photographyOrderMapper.selectById(201L)).thenReturn(order);
        when(transactionMapper.selectById(102L)).thenReturn(finalTransaction);
        when(accountMapper.selectById(11L)).thenReturn(account);
        when(accountMapper.selectByIds(any())).thenReturn(List.of(account));

        doAnswer(invocation -> {
            order.setStatus("cancelled");
            order.setFinalAccountId(null);
            order.setFinalTransactionId(null);
            order.setFinalReceivedAt(null);
            return 1;
        }).when(photographyOrderMapper).update(any(), any());

        PhotographyOrderResponse response = photographyOrderService.cancel(201L, 1L);

        assertThat(response.getStatus()).isEqualTo("cancelled");
        assertThat(response.getDepositTransactionId()).isEqualTo(101L);
        assertThat(response.getDepositReceivedAt()).isNotNull();
        assertThat(response.getFinalTransactionId()).isNull();
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("200.00");
        assertThat(finalTransaction.getStatus()).isEqualTo("voided");
        verify(transactionMapper, never()).selectById(101L);
    }
}
