package com.example.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.finance.entity.SalaryMonthRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SalaryMonthRecordMapper extends BaseMapper<SalaryMonthRecordEntity> {

    @Insert("""
        INSERT INTO salary_month_records (user_id, salary_month, gross_salary, note)
        VALUES (#{userId}, #{salaryMonth}, #{grossSalary}, #{note})
        ON DUPLICATE KEY UPDATE id = id
        """)
    int insertAutoIfAbsent(
        @Param("userId") Long userId,
        @Param("salaryMonth") LocalDate salaryMonth,
        @Param("grossSalary") BigDecimal grossSalary,
        @Param("note") String note
    );
}
