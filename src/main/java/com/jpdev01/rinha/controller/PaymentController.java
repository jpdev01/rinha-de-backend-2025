package com.jpdev01.rinha.controller;

import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.service.PaymentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }


    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/payments")
    public void payments(@RequestBody SavePaymentRequestDTO payment) {
        paymentService.process(payment);
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/purge-payments")
    public void purgePayments() {
        paymentService.purge();
    }

    @GetMapping("/payments-summary")
    public ResponseEntity<PaymentSummaryResponseDTO> paymentsSummary(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @RequestParam("to") LocalDateTime to
    ) {
        return ResponseEntity.ok(paymentService.getPayments(from, to));
    }
}