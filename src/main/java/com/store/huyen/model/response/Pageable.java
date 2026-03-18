package com.store.huyen.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pageable implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("_totalElements")
    private long totalElements;

    @JsonProperty("_currentPage")
    private long currentPage;

    @JsonProperty("_pageSize")
    private long pageSize;

    @JsonProperty("_totalPages")
    private long totalPages;

}
