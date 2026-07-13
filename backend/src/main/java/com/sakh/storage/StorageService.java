package com.sakh.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String store(MultipartFile file);

    Resource load(String storagePath);

    void delete(String storagePath);
}