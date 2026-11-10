package com.example.app.dto;

import java.math.BigDecimal;
import java.util.List;

/** 账户列表与总资产共用本次读取的余额，避免前端重复请求和估值。 */
public record AccountSummaryResponse(List<AccountResponse> accounts, BigDecimal totalAssets) {}
