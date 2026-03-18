package com.store.huyen.configuration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@Configuration
@RequiredArgsConstructor
public class SpringDataRestConfig implements RepositoryRestConfigurer {
    private final EntityManager entityManager;

    @Override
    public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config,
                                                     CorsRegistry cors) {
        config.setPageParamName("_currentPage");
        config.setLimitParamName("_pageSize");
        config.setSortParamName("_sort");
        config.setDefaultMediaType(MediaType.APPLICATION_JSON);
        config.setDefaultPageSize(20);
        config.setReturnBodyOnCreate(true);
        config.setReturnBodyOnUpdate(true);
        config.exposeIdsFor(entityManager.getMetamodel().getEntities()
                .stream()
                .map(Type::getJavaType)
                .toArray(Class[]::new));
    }
}
