package com.example.shop.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Practice 6: publishes the API as OpenAPI, browsable in Swagger UI (lecture week 10, slides 8 and 11).
 *
 * <p>springdoc scans the controllers at start-up and generates the spec from the existing
 * {@code @RestController}, {@code @RequestMapping} and DTO types — no hand-written document.
 * This config only adds the title/version and one group per API version (slide 11), which the course
 * versions by URI path ({@code /api/v1}) since week 4.
 *
 * <ul>
 *   <li>Swagger UI: <code>/swagger-ui.html</code></li>
 *   <li>Raw OpenAPI JSON: <code>/v3/api-docs</code></li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    /** Name of the security scheme; Swagger UI's Authorize button uses it (lecture week 10, slide 11). */
    public static final String BEARER_JWT = "bearer-jwt";

    @Bean
    public OpenAPI shopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Online Shop API")
                        .version("v1")
                        .description("Categories, products and orders. Exchange rates are fetched from an external "
                                + "service with configured timeouts and a fallback (Practice 6). "
                                + "Log in with POST /api/v1/auth/login, press Authorize and paste the accessToken "
                                + "(Practice 7). Reading the catalogue needs no token."))
                // Practice 7: the API accepts a Bearer JWT. Declared once here, applied to every operation;
                // the login operation opts out with an empty @SecurityRequirements.
                .components(new Components().addSecuritySchemes(BEARER_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }

    // One group per version -> a "v1" entry in Swagger UI; a v2 group would be added only for a breaking change
    @Bean
    public GroupedOpenApi v1Api() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
