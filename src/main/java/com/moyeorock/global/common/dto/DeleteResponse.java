package com.moyeorock.global.common.dto;

public record DeleteResponse(Long id, boolean deleted) {

    public static DeleteResponse of(Long id) {
        return new DeleteResponse(id, true);
    }
}
