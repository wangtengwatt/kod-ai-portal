package com.kod.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** Creates only the additive KAI identity-link table when explicitly enabled. */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class IdentitySchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final IdentityProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSchemaInitEnabled()) {
            return;
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("identity-schema.sql"));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
        log.info("KAI Identity account-link schema is ready");
    }
}
