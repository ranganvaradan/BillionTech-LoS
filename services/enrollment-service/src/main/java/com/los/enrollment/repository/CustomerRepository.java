package com.los.enrollment.repository;

import com.los.enrollment.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByMobile(String mobile);

    Optional<Customer> findByEmail(String email);

    boolean existsByMobile(String mobile);

    boolean existsByEmail(String email);
}
