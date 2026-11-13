package com.groupthree.Facilitybook.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI facilityBookOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FacilityBook API")
                        .version("1.0.0")
                        .description("""
                                Facility & Resource Reservation System.

                                All business endpoints require a JWT obtained from `POST /api/auth/login`.
                                Click **Authorize** and paste the token (without the `Bearer ` prefix).
                                """)
                        .contact(new Contact().name("Group Three")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
