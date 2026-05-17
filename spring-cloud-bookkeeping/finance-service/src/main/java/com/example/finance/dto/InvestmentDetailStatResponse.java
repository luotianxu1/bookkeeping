package com.example.finance.dto;

import lombok.Data;

@Data
public class InvestmentDetailStatResponse {
    private String label;
    private String value;
    private String tone;

    public InvestmentDetailStatResponse() {
    }

    public InvestmentDetailStatResponse(String label, String value, String tone) {
        this.label = label;
        this.value = value;
        this.tone = tone;
    }
}
