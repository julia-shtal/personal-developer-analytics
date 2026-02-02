package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.UpdateDataSourceRequest;
import com.juliashtal.devanalytics.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/datasources")
@PreAuthorize("isAuthenticated()")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @PostMapping
    public ResponseEntity<DataSourceConfig> create(@RequestBody @Valid CreateDataSourceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig created = dataSourceService.create(userId, request);
        return ResponseEntity
                .created(URI.create("/api/datasources/" + created.getId()))
                .body(created);
    }

    @GetMapping
    public List<DataSourceConfig> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.listForUser(userId);
    }

    @GetMapping("/{id}")
    public DataSourceConfig get(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.getForUser(userId, id);
    }

    @PutMapping("/{id}")
    public DataSourceConfig update(@PathVariable Long id,
                                   @RequestBody UpdateDataSourceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        dataSourceService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}


