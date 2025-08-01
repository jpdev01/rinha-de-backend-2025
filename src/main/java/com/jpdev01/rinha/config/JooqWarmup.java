package com.jpdev01.rinha.config;

import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import static com.jpdev01.rinha.jooq.Tables.PAYMENTS;

@Component
public class JooqWarmup implements ApplicationRunner {

    private final DSLContext dsl;

    public JooqWarmup(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void run(ApplicationArguments args) {
        dsl.selectFrom(PAYMENTS).limit(1).fetch();
    }
}
