package com.jpdev01.rinha.repository;

import com.jpdev01.rinha.entity.PaymentEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PaymentRepository extends ReactiveCrudRepository<PaymentEntity, UUID>, PaymentRepositoryCustom {

}