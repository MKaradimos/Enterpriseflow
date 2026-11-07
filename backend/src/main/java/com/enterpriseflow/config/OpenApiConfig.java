package com.enterpriseflow.config;

import com.enterpriseflow.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger metadata plus the bearer scheme behind the Authorize button. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // CurrentUser is resolved from the token, not sent by the client; hide it from the generated spec.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    OpenAPI enterpriseFlowOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        // BuildProperties exists only when the build-info goal ran (absent in some IDE runs).
        String version = buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");

        return new OpenAPI()
                .info(new Info()
                        .title("EnterpriseFlow API")
                        .description("Enterprise project and workflow management platform. "
                                + "Obtain a token with POST /api/v1/auth/login, then use the Authorize button.")
                        .version(version))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
