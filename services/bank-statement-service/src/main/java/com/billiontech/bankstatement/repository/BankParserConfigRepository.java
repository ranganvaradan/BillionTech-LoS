package com.billiontech.bankstatement.repository;

import com.billiontech.bankstatement.model.entity.BankParserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankParserConfigRepository extends JpaRepository<BankParserConfig, Long> {
    Optional<BankParserConfig> findByBankCode(String bankCode);
    List<BankParserConfig> findByIsActiveTrue();
}
