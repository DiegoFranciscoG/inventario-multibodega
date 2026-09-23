package io.github.diegofranciscog.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI inventoryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("inventario-multibodega API")
                        .version("1.0.0")
                        .description("WMS multibodega: kárdex valorizado (costo promedio ponderado, NIC 2), lotes con "
                                + "vencimiento, transferencias atómicas, conteo cíclico aprobado y escaneo GS1/QR.")
                        .license(new License().name("MIT").url("https://opensource.org/license/mit")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
