package com.mmo.handler;

import com.mmo.model.response.Pageable;
import com.mmo.model.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ControllerAdvice
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NonNull final MethodParameter returnType,
                            @NonNull final Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, @NonNull final MethodParameter returnType,
                                  @NonNull final MediaType selectedContentType,
                                  @NonNull final Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull final ServerHttpRequest request, @NonNull final ServerHttpResponse response) {
        if (body instanceof RepresentationModel) {
            return this.process((RepresentationModel<?>) body);
        }
        if (body instanceof ByteArrayResource) {
            return body;
        }
        if (body instanceof Page<?> page) {
            return Response.builder()
                    .data(page.getContent())
                    .page(Pageable.builder()
                            .totalElements(page.getTotalElements())
                            .currentPage(page.getNumber())
                            .pageSize(page.getSize())
                            .totalPages(page.getTotalPages())
                            .build())
                    .build();
        }

        return Response.builder()
                .data(body)
                .build();
    }

    @SuppressWarnings("unchecked")
    public Object process(@NonNull RepresentationModel<?> model) {
        if (model instanceof PagedModel) {
            return processPagedModel((PagedModel<EntityModel<?>>) model);
        } else if (model instanceof CollectionModel) {
            return processCollectionModel((CollectionModel<EntityModel<?>>) model);
        } else if (model instanceof EntityModel) {
            return processEntityModel((EntityModel<?>) model);
        }

        return Response.builder().data(model).build();
    }

    private Object processPagedModel(PagedModel<EntityModel<?>> pagedModel) {
        List<?> content = extractContent(pagedModel.getContent());
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String pageParam = request.getParameter("page");
        String sizeParam = request.getParameter("size");

        if (pageParam != null || sizeParam != null) {
            PagedModel.PageMetadata metadata = pagedModel.getMetadata();
            int page = metadata != null ? (int) metadata.getNumber() : 0;
            int size = metadata != null ? (int) metadata.getSize() : 0;
            long totalElements = metadata != null ? metadata.getTotalElements() : content.size();
            int totalPages = metadata != null ? (int) metadata.getTotalPages() : 1;

            Pageable pageable = Pageable.builder()
                    .currentPage(page)
                    .pageSize(size)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .build();
            return Response.builder().data(content).page(pageable).build();
        } else {
            return Response.builder().data(content).build();
        }
    }

    private Object processCollectionModel(CollectionModel<EntityModel<?>> collectionModel) {
        List<?> content = extractContent(collectionModel.getContent());
        return Response.builder().data(content).build();
    }

    private Object processEntityModel(EntityModel<?> entityModel) {
        Object content = entityModel.getContent();
        return Response.builder().data(content).build();
    }

    private List<?> extractContent(Collection<?> contentList) {
        List<Object> result = new ArrayList<>();
        for (Object item : contentList) {
            if (item instanceof EntityModel) {
                result.add(((EntityModel<?>) item).getContent());
            }
        }
        return result;
    }

}
