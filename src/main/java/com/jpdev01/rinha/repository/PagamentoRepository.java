package com.jpdev01.rinha.repository;

import com.jpdev01.rinhadebackend.entity.PagamentoDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PagamentoRepository extends MongoRepository<PagamentoDocument, String> {
}