package com.moyeorock.global.file.service;

import java.io.IOException;

// SizeLimitedInputStream이 던져서 FileStorageClient 구현체를 거쳐도(UncheckedIOException으로
// 감싸져도) FileService에서 이 타입만 보고 FILE_SIZE_EXCEEDED로 구분해 번역할 수 있게 한다.
class UploadSizeExceededException extends IOException {

    UploadSizeExceededException(String message) {
        super(message);
    }
}
