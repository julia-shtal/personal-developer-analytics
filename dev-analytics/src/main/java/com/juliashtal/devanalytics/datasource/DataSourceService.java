package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.user.User;
import com.juliashtal.devanalytics.datasource.model.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.UpdateDataSourceRequest;
import com.juliashtal.devanalytics.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.repository.UserRepository;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class DataSourceService {

    private final DataSourceConfigRepository repository;
    private final UserRepository userRepository;
    private final SimpleTokenEncryptor tokenEncryptor;
    private final DataSourceValidator validator;

    public DataSourceService(
            DataSourceConfigRepository repository,
            UserRepository userRepository,
            SimpleTokenEncryptor tokenEncryptor,
            DataSourceValidator validator
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.tokenEncryptor = tokenEncryptor;
        this.validator = validator;
    }

    @Transactional
    public DataSourceConfig create(Long userId, CreateDataSourceRequest req) {
        validator.validateCreate(req);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setUser(user);
        cfg.setType(req.getType());
        cfg.setName(req.getName());
        cfg.setBaseUrl(req.getBaseUrl());
        cfg.setPath(req.getPath());
        if (req.getApiToken() != null && !req.getApiToken().isBlank()) {
            cfg.setApiTokenEncrypted(tokenEncryptor.encrypt(req.getApiToken()));
        }
        cfg.setEnabled(true);

        return repository.save(cfg);
    }

    @Transactional(readOnly = true)
    public List<DataSourceConfig> listForUser(Long userId) {
        User user = userRepository.getReferenceById(userId);
        return repository.findAllByUser(user);
    }

    @Transactional(readOnly = true)
    public DataSourceConfig getForUser(Long userId, Long id) {
        User user = userRepository.getReferenceById(userId);
        return repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("DataSource not found: " + id));
    }

    @Transactional
    public DataSourceConfig update(Long userId, Long id, UpdateDataSourceRequest req) {
        DataSourceConfig cfg = getForUser(userId, id);

        if (req.getName() != null) {
            cfg.setName(req.getName());
        }
        if (req.getBaseUrl() != null) {
            cfg.setBaseUrl(req.getBaseUrl());
        }
        if (req.getPath() != null) {
            cfg.setPath(req.getPath());
        }
        if (req.getApiToken() != null) {
            cfg.setApiTokenEncrypted(tokenEncryptor.encrypt(req.getApiToken()));
        }
        if (req.getEnabled() != null) {
            cfg.setEnabled(req.getEnabled());
        }

        return repository.save(cfg);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        DataSourceConfig cfg = getForUser(userId, id);
        repository.delete(cfg);
    }
}

