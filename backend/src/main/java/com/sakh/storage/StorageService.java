package com.sakh.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String store(MultipartFile file);

    void delete(String storagePath);
}