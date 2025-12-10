package com.example.image_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PythonRequest {
	private String imageBase64;
	private int maskSize;
	private String filter;
}
