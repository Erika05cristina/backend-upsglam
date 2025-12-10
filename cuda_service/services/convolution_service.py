# app/services/convolution_service.py

import math
import base64
from io import BytesIO

import numpy as np
from fastapi import HTTPException, UploadFile
from PIL import Image

from cuda_service.utils.image_utils import load_image_to_array
from cuda_service.cuda.mask_builder import build_kernel, validate_kernel_vs_image
from cuda_service.cuda.kernels import (
    run_sobel_gpu,
    run_convolution_gray_gpu,
    run_convolution_rgb_gpu,
    run_mean_rgb_gpu,
    # run_ups_filter_gpu,  # 👈 YA NO SE USA
)


async def process_convolution(
    image: UploadFile,
    filter_type: str,
    kernel_size: int,
):
    """
    Servicio principal de convolución.

    Filtros soportados:
        * sobel    -> run_sobel_gpu (grayscale)
        * gaussian -> run_convolution_rgb_gpu (RGB)
        * emboss   -> run_convolution_gray_gpu (grayscale)
        * mean     -> run_mean_rgb_gpu (RGB, promedio NxN)
    """

    # 1) Cargar imagen a NumPy (float32) en escala de grises
    try:
        img_array_f32, width, height = await load_image_to_array(
            image,
            color_mode="grayscale"
        )
    except Exception as e:
        raise HTTPException(
            status_code=400,
            detail=f"No se pudo leer la imagen: {e}"
        )

    # Versión uint8 para los kernels que lo requieren (sobel / mean)
    img_array_u8 = img_array_f32.astype(np.uint8)

    # 2) Validar filtro
    filter_type = filter_type.lower()
    if filter_type not in {"gaussian", "sobel", "emboss", "mean"}:
        raise HTTPException(
            status_code=400,
            detail="filter_type debe ser uno de: 'gaussian', 'sobel', 'emboss' o 'mean'."
        )

    # 3) Validar que el kernel quepa en la imagen
    try:
        validate_kernel_vs_image(kernel_size, width, height)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    # 4) Configuración CUDA
    threads_x = 16
    threads_y = 16
    blocks_x = math.ceil(width / threads_x)
    blocks_y = math.ceil(height / threads_y)

    # 5) Construir kernel SOLO para gaussian/sobel/emboss (no mean)
    kernel_preview = {}
    is_preset = False
    kernel_info = None

    if filter_type in {"gaussian", "sobel", "emboss"}:
        try:
            kernel_info = build_kernel(filter_type, kernel_size, allow_custom=True)
        except ValueError as e:
            raise HTTPException(status_code=400, detail=str(e))

        is_preset = kernel_info.get("is_preset", False)

        if filter_type == "sobel":
            Kx = kernel_info["Kx"]
            Ky = kernel_info["Ky"]
            kernel_preview = {
                "Kx_shape": list(Kx.shape),
                "Ky_shape": list(Ky.shape),
                "Kx_first_values": Kx.flatten()[:12].tolist(),
                "Ky_first_values": Ky.flatten()[:12].tolist()
            }
        else:
            K = kernel_info["K"]
            kernel_preview = {
                "K_shape": list(K.shape),
                "K_first_values": K.flatten()[:12].tolist()
            }
    else:
        # mean: no tiene kernel explícito, solo descripción
        kernel_preview = {
            "description": f"mean {kernel_size}x{kernel_size}"
        }

    # 6) Ejecutar filtro en GPU según tipo
    gpu_time_ms = None

    if filter_type == "sobel":
        # Sobel en escala de grises
        out_u8, gpu_time_ms = run_sobel_gpu(
            gray_u8=img_array_u8,
            kernel_size=kernel_size,
            threads_x=threads_x,
            threads_y=threads_y,
            blocks_x=blocks_x,
            blocks_y=blocks_y,
        )

    elif filter_type == "gaussian":
        # --- GAUSSIANO A COLOR (RGB) ---
        K = kernel_info["K"].astype(np.float32)

        # Volver al inicio del archivo para leer de nuevo en RGB
        image.file.seek(0)
        pil_img = Image.open(image.file).convert("RGB")
        img_rgb_f32 = np.array(pil_img).astype(np.float32)

        out_rgb_f32, gpu_time_ms = run_convolution_rgb_gpu(
            img_rgb_f32=img_rgb_f32,
            kernel_f32=K,
            threads_x=threads_x,
            threads_y=threads_y,
            blocks_x=blocks_x,
            blocks_y=blocks_y,
        )

        out_u8 = np.clip(out_rgb_f32, 0, 255).astype(np.uint8)

    elif filter_type == "mean":
        # --- MEAN RGB (promedio cana a canal) ---
        image.file.seek(0)
        pil_img = Image.open(image.file).convert("RGB")
        img_rgb_u8 = np.array(pil_img).astype(np.uint8)
        out_u8, gpu_time_ms = run_mean_rgb_gpu(
            img_rgb_u8=img_rgb_u8,
            kernel_size=kernel_size,
            threads_x=threads_x,
            threads_y=threads_y,
            blocks_x=blocks_x,
            blocks_y=blocks_y,
        )

    else:
        # --- EMBOSS en escala de grises ---
        K = kernel_info["K"].astype(np.float32)

        out_f32, gpu_time_ms = run_convolution_gray_gpu(
            gray_f32=img_array_f32,
            kernel_f32=K,
            threads_x=threads_x,
            threads_y=threads_y,
            blocks_x=blocks_x,
            blocks_y=blocks_y,
        )

        # Offset típico para emboss
        out_f32 = out_f32 + 128.0

        out_u8 = np.clip(out_f32, 0, 255).astype(np.uint8)

    # 7) Convertir resultado a base64
    pil_img = Image.fromarray(out_u8)
    buffer = BytesIO()
    pil_img.save(buffer, format="PNG")
    processed_image_b64 = base64.b64encode(buffer.getvalue()).decode("utf-8")

    return {
        "success": True,
        "message": f"Filtro {filter_type} ejecutado en GPU.",
        "image_info": {
            "width": width,
            "height": height
        },
        "filter": {
            "type": filter_type,
            "kernel_size": kernel_size,
            "is_preset": is_preset
        },
        "cuda_config": {
            "threads_x": threads_x,
            "threads_y": threads_y,
            "blocks_x": blocks_x,
            "blocks_y": blocks_y,
            "threads_per_block": threads_x * threads_y
        },
        "kernel_preview": kernel_preview,
        "gpu_info": {
            "gpu_time_ms": gpu_time_ms
        },
        "result": {
            "image_base64": processed_image_b64,
            "format": "PNG" if processed_image_b64 else None
        }
    }
