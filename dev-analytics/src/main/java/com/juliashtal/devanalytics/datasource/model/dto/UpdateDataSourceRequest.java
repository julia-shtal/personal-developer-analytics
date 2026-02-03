package com.juliashtal.devanalytics.datasource.model.dto;

import lombok.Data;

@Data
public class UpdateDataSourceRequest {
    private String name;
    private String baseUrl;
    private String path;
    private String apiToken;
    private Boolean enabled;
}

