package com.juliashtal.devanalytics.issue.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository repository;

    public IssueEntity getIssue(Long issueId) {
        return repository.findById(issueId)
                .orElseThrow(() -> new NoSuchElementException("Issue not found: " + issueId));
    }

    public Page<IssueEntity> getByDataSource(DataSourceConfig source, Pageable pageable) {
        return repository.findByDataSource(source, pageable);
    }
}

