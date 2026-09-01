package com.steamfolder.steamfolder.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CursorPageResponseDto<T> {

    private List<T> content;
    private Long nextCursor;
    private boolean hasNext;
    private long totalElements;
    private int pageSize;

    public static <T> CursorPageResponseDto<T> of(List<T> content, Long nextCursor, boolean hasNext, long totalElements, int pageSize) {
        return CursorPageResponseDto.<T>builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .totalElements(totalElements)
                .pageSize(pageSize)
                .build();
    }
}
