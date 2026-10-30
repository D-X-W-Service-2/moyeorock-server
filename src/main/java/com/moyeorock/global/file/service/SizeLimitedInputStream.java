package com.moyeorock.global.file.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

// 로컬 업로드 수신(receiveUpload)은 raw PUT body를 그대로 디스크에 흘려보내서
// spring.servlet.multipart.max-file-size가 적용되지 않는다 — 그 자리를 대신해 상한을 강제한다.
class SizeLimitedInputStream extends FilterInputStream {

    private final long maxBytes;
    private long readBytes;

    SizeLimitedInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            checkLimit(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            checkLimit(n);
        }
        return n;
    }

    private void checkLimit(int n) throws IOException {
        readBytes += n;
        if (readBytes > maxBytes) {
            throw new UploadSizeExceededException("업로드 용량 제한(%d bytes)을 초과했습니다.".formatted(maxBytes));
        }
    }
}
