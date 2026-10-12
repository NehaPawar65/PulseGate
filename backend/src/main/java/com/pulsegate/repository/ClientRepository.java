package com.pulsegate.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.pulsegate.entity.Client;

public interface ClientRepository extends JpaRepository<Client, Long> {
    // Loads the policy in the same query: one DB read per gateway request.
    @EntityGraph(attributePaths = "policy")
    Optional<Client> findByApiKeyHash(String apiKeyHash);

    boolean existsByName(String name);

    @EntityGraph(attributePaths = "policy")
    List<Client> findAllByOrderByIdAsc();
}
