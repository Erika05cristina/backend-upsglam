package com.example.post_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CudaMetadataDto {
    private String filterType;
    private Integer kernelSize;
    private Integer width;
    private Integer height;
    private Double gpuTimeMs;
    private Integer blocksX;
    private Integer blocksY;
    private Integer threadsX;
    private Integer threadsY;
    private Integer threadsPerBlock;
}
