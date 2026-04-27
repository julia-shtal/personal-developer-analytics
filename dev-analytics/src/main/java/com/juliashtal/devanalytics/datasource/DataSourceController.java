package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.dto.DataSourceResponseDto;
import com.juliashtal.devanalytics.datasource.model.dto.UpdateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.service.DataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/datasources")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final DataSourceCollectService dataSourceCollectService;

    @PostMapping
    public ResponseEntity<DataSourceConfig> create(@RequestBody @Valid CreateDataSourceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig created = dataSourceService.create(userId, request);
        return ResponseEntity
                .created(URI.create("/api/datasources/" + created.getId()))
                .body(created);
    }

    @GetMapping
    public List<DataSourceResponseDto> list() {
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

    @PostMapping("/{id}/collect")
    public ResponseEntity<String> collect(@PathVariable Long id) {
        String result = dataSourceCollectService.collectForDataSource(id);
        return ResponseEntity.ok(result);
    }
}


