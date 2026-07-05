package com.example.insurancecrm.repository;

import com.example.insurancecrm.domain.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface CustomerRepository extends MongoRepository<Customer, String> {

    List<Customer> findByAssignedAgentId(String agentId);

    long countByAssignedAgentId(String agentId);

    @Query("{ $or: [ { 'name': { $regex: ?0, $options: 'i' } }, { 'phone': { $regex: ?0, $options: 'i' } } ] }")
    List<Customer> searchByNameOrPhone(String query);
}
