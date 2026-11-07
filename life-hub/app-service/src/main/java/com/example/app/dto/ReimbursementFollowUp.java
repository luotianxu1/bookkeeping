package com.example.app.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ReimbursementFollowUp(@Size(max = 100) String recipient, LocalDate expectedDate) {}
