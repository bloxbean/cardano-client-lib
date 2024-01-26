package com.bloxbean.cardano.client.supplier.ogmios.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseRequestDto<T> {

    private String jsonrpc = "2.0";
    private String method;
    private String id;
    private Map<String, Map<String, String>> params;
    private T result;

    public BaseRequestDto(String method) {
        this.method = method;
    }

    public BaseRequestDto(String method, Map<String, Map<String, String>> params) {
        this.method = method;
        this.params = params;
    }

    @Override
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
