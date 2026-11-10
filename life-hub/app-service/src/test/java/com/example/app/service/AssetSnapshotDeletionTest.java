package com.example.app.service;

import com.example.app.entity.AccountEntity;
import com.example.app.entity.AssetDailySnapshotEntity;
import com.example.app.mapper.AccountMapper;
import com.example.app.mapper.AccountTypeMapper;
import com.example.app.mapper.AssetDailySnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetSnapshotDeletionTest {
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    AccountMapper accounts;
    AssetDailySnapshotMapper snapshots;
    AccountService accountService;
    AssetSnapshotService service;

    @BeforeEach void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE asset_daily_snapshots(user_id BIGINT,account_id BIGINT,total_assets DECIMAL(18,2))");
        jdbc.update("INSERT INTO users VALUES(1)");
        var manager = new DataSourceTransactionManager(data);
        transaction = new TransactionTemplate(manager);
        accounts = mock(AccountMapper.class);
        snapshots = mock(AssetDailySnapshotMapper.class);
        accountService = mock(AccountService.class);
        AccountEntity candidate = new AccountEntity();
        candidate.setId(1L); candidate.setUserId(1L);
        when(accounts.selectList(any())).thenReturn(List.of(candidate));
        when(accounts.lockUser(1L)).thenAnswer(call -> jdbc.queryForList("SELECT id FROM users WHERE id=1 FOR UPDATE", Long.class).stream().findFirst().orElse(null));
        when(accountService.listNetWorthAccounts(1L, "active")).thenReturn(List.of());
        when(accountService.calculateTotalAssets(anyList())).thenReturn(BigDecimal.ZERO);
        when(snapshots.insert(any(AssetDailySnapshotEntity.class))).thenAnswer(call -> {
            AssetDailySnapshotEntity row = call.getArgument(0);
            return jdbc.update("INSERT INTO asset_daily_snapshots VALUES(?,?,?)", row.getUserId(), row.getAccountId(), row.getTotalAssets());
        });
        service = new AssetSnapshotService(accounts, mock(AccountTypeMapper.class), snapshots, accountService, manager);
    }

    @Test void staleScheduledCandidatesCannotRecreateSnapshotsAfterUserDeletion() {
        jdbc.update("DELETE FROM users WHERE id=1");
        assertEquals(0, service.captureDailySnapshots(LocalDate.now()));
        verifyNoInteractions(accountService, snapshots);
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM asset_daily_snapshots", Long.class));
    }

    @Test void deletionWaitsForSnapshotTransactionAndRemovesItsCommittedRows() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch finishSnapshot = new CountDownLatch(1);
        CountDownLatch deleting = new CountDownLatch(1);
        when(accountService.listNetWorthAccounts(1L, "active")).thenAnswer(call -> {
            reading.countDown();
            assertTrue(finishSnapshot.await(5, TimeUnit.SECONDS));
            return List.of();
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var snapshot = executor.submit(() -> service.captureDailySnapshots(LocalDate.now()));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            var deletion = executor.submit(() -> transaction.executeWithoutResult(status -> {
                deleting.countDown();
                jdbc.queryForList("SELECT id FROM users WHERE id=1 FOR UPDATE", Long.class);
                jdbc.update("DELETE FROM asset_daily_snapshots WHERE user_id=1");
                jdbc.update("DELETE FROM users WHERE id=1");
            }));
            assertTrue(deleting.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> deletion.get(150, TimeUnit.MILLISECONDS));
            finishSnapshot.countDown();
            assertEquals(1, snapshot.get(5, TimeUnit.SECONDS));
            deletion.get(5, TimeUnit.SECONDS);
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM asset_daily_snapshots", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class));
        } finally {
            finishSnapshot.countDown();
            executor.shutdownNow();
        }
    }
}
