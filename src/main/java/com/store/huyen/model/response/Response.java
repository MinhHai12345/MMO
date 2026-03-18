package com.store.huyen.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response<T> extends RepresentationModel<Response<T>> implements Serializable {
    @Serial
    private static final long serialVersionUID = 4006066927468782730L;

    private T data;
    private List<Error> errors;
    private Pageable page;

    @Builder.Default
    @Setter(AccessLevel.NONE)
    private LocalDateTime timestamp = LocalDateTime.now();

    public Response(T data) {
        this.data = data;
    }
}


