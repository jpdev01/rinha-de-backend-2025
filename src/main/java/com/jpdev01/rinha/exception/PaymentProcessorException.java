package com.jpdev01.rinha.exception;

public class PaymentProcessorException extends RuntimeException {

    public PaymentProcessorException(String message ) {
        super( message );
    }
}