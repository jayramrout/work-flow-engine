package com.athenahealth.workflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workflowEngineOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Workflow Engine API")
                        .description("""
                                REST API for creating, managing, and executing multi-step workflows \
                                with distributed workers. Workflows are defined as Directed Acyclic Graphs \
                                (DAGs) where each step declares its upstream dependencies and retry configuration.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("athenahealth")
                                .email("support@athenahealth.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080/api").description("Local development"),
                        new Server().url("http://localhost:8081/api").description("Worker node 1")
                ));
    }
}

