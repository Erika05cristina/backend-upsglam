# app/cuda/kernels.py

import numpy as np
import pycuda.autoinit  # inicializa contexto
import pycuda.driver as drv
from pycuda.compiler import SourceModule

from .mask_builder import generate_sobel_masks

UPS_FILTER_KERNEL_CODE = r"""
__global__ void ups_color_highlight(
    unsigned char* img,   // imagen RGB de entrada
    unsigned char* out,   // imagen RGB de salida
    int width,
    int height,
    int pitch             // width * 3
){
    int x = blockIdx.x * blockDim.x + threadIdx.x;
    int y = blockIdx.y * blockDim.y + threadIdx.y;

    if (x >= width || y >= height) return;

    int idx = y * pitch + x * 3;

    // Leer RGB (Pillow da RGB, no BGR)
    unsigned char r8 = img[idx + 0];
    unsigned char g8 = img[idx + 1];
    unsigned char b8 = img[idx + 2];

    // Normalizar a [0,1]
    float r = r8 / 255.0f;
    float g = g8 / 255.0f;
    float b = b8 / 255.0f;

    // ---- RGB -> HSV (formato tipo OpenCV) ----
    float maxv = fmaxf(r, fmaxf(g, b));
    float minv = fminf(r, fminf(g, b));
    float delta = maxv - minv;

    float H_deg = 0.0f;
    if (delta > 1e-6f) {
        if (maxv == r) {
            H_deg = 60.0f * fmodf(((g - b) / delta), 6.0f);
        } else if (maxv == g) {
            H_deg = 60.0f * (((b - r) / delta) + 2.0f);
        } else { // maxv == b
            H_deg = 60.0f * (((r - g) / delta) + 4.0f);
        }
        if (H_deg < 0.0f) H_deg += 360.0f;
    }

    float S = (maxv <= 0.0f) ? 0.0f : (delta / maxv);
    float V = maxv;

    // Escala tipo OpenCV: H [0,179], S [0,255], V [0,255]
    unsigned char H = (unsigned char)(H_deg / 2.0f + 0.5f);
    unsigned char S8 = (unsigned char)(S * 255.0f + 0.5f);
    unsigned char V8 = (unsigned char)(V * 255.0f + 0.5f);

    // Rango azul
    bool in_blue =
        (H >= 90  && H <= 140) &&
        (S8 >= 50  && S8 <= 255) &&
        (V8 >= 40  && V8 <= 255);

    // Rango amarillo
    bool in_yellow =
        (H >= 15  && H <= 40) &&
        (S8 >= 70  && S8 <= 255) &&
        (V8 >= 70  && V8 <= 255);

    if (in_blue || in_yellow) {
        // Mantener color original
        out[idx + 0] = r8;
        out[idx + 1] = g8;
        out[idx + 2] = b8;
    } else {
        // Convertir a gris (coeficientes típicos en RGB)
        unsigned char gray = (unsigned char)(0.299f * r8 + 0.587f * g8 + 0.114f * b8);
        out[idx + 0] = gray;
        out[idx + 1] = gray;
        out[idx + 2] = gray;
    }
}
"""

_ups_mod = SourceModule(UPS_FILTER_KERNEL_CODE)
_ups_filter_gpu = _ups_mod.get_function("ups_color_highlight")

def run_ups_filter_gpu(
    img_rgb_u8: np.ndarray,
    threads_x: int,
    threads_y: int,
    blocks_x: int,
    blocks_y: int,
):
    """
    Aplica el filtro institucional (solo azul y amarillo en color,
    resto en gris) sobre una imagen RGB uint8 (H, W, 3).
    Devuelve (out_rgb_u8, gpu_time_ms).
    """
    if img_rgb_u8.ndim != 3 or img_rgb_u8.shape[2] != 3:
        raise ValueError("Imagen debe ser RGB (H, W, 3).")

    img_rgb_u8 = np.ascontiguousarray(img_rgb_u8.astype(np.uint8))
    H, W, _ = img_rgb_u8.shape
    pitch = W * 3

    # Reservar memoria en GPU
    d_in = drv.mem_alloc(img_rgb_u8.nbytes)
    d_out = drv.mem_alloc(img_rgb_u8.nbytes)

    drv.memcpy_htod(d_in, img_rgb_u8)

    block = (threads_x, threads_y, 1)
    grid = (blocks_x, blocks_y, 1)

    start_evt = drv.Event()
    end_evt = drv.Event()

    start_evt.record()
    _ups_filter_gpu(
        d_in,
        d_out,
        np.int32(W),
        np.int32(H),
        np.int32(pitch),
        block=block,
        grid=grid
    )
    end_evt.record()
    end_evt.synchronize()
    gpu_ms = start_evt.time_till(end_evt)

    out_rgb = np.empty_like(img_rgb_u8)
    drv.memcpy_dtoh(out_rgb, d_out)

    return out_rgb, gpu_ms

# -------------------------------------------------------------------
#  CÓDIGO CUDA PARA SOBEL
# -------------------------------------------------------------------
SOBEL_KERNEL_CODE = r"""
__global__ void sobel_mag(
    unsigned char *img,
    float *mag,
    float *Kx, float *Ky,
    int width, int height,
    int n
){
    int x = blockIdx.x * blockDim.x + threadIdx.x;
    int y = blockIdx.y * blockDim.y + threadIdx.y;

    if (x >= width || y >= height) return;

    int pad = n / 2;
    float gx = 0.0f;
    float gy = 0.0f;

    for (int i = 0; i < n; i++){
        for (int j = 0; j < n; j++){
            int xx = x + j - pad;
            int yy = y + i - pad;

            if (xx < 0) xx = 0;
            if (yy < 0) yy = 0;
            if (xx >= width) xx = width - 1;
            if (yy >= height) yy = height - 1;

            unsigned char pixel = img[yy * width + xx];

            gx += pixel * Kx[i*n + j];
            gy += pixel * Ky[i*n + j];
        }
    }

    mag[y * width + x] = sqrtf(gx * gx + gy * gy);
}

__global__ void normalize_mag(
    float *mag,
    unsigned char *out,
    float max_val,
    int width, int height
){
    int x = blockIdx.x * blockDim.x + threadIdx.x;
    int y = blockIdx.y * blockDim.y + threadIdx.y;

    if (x >= width || y >= height) return;

    float v = mag[y*width + x];
    v = (v / max_val) * 255.0f;

    if (v < 0) v = 0;
    if (v > 255) v = 255;

    out[y*width + x] = (unsigned char)v;
}
"""

# -------------------------------------------------------------------
#  Promedio (MEAN)
# -------------------------------------------------------------------
MEAN_KERNEL_CODE = r"""
__global__ void mean_filter(
    unsigned char *img,
    unsigned char *out,
    int width,
    int height,
    int n
){
    int x = blockIdx.x * blockDim.x + threadIdx.x;
    int y = blockIdx.y * blockDim.y + threadIdx.y;

    if (x >= width || y >= height) return;

    int pad = n / 2;
    float sum = 0.0f;

    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            int xx = x + j - pad;
            int yy = y + i - pad;

            // Padding por borde (clamp)
            if (xx < 0) xx = 0;
            if (yy < 0) yy = 0;
            if (xx >= width)  xx = width  - 1;
            if (yy >= height) yy = height - 1;

            unsigned char pixel = img[yy * width + xx];
            sum += (float)pixel;
        }
    }

    float denom = (float)(n * n);
    float val = sum / denom;  // promedio

    if (val < 0.0f)   val = 0.0f;
    if (val > 255.0f) val = 255.0f;

    out[y * width + x] = (unsigned char)(val + 0.5f);
}
"""

# Compilar módulos CUDA
_sobel_mod = SourceModule(SOBEL_KERNEL_CODE)
_sobel_mag_gpu = _sobel_mod.get_function("sobel_mag")
_normalize_gpu = _sobel_mod.get_function("normalize_mag")

_mean_mod = SourceModule(MEAN_KERNEL_CODE)
_mean_filter_gpu = _mean_mod.get_function("mean_filter")


def run_sobel_gpu(
    gray_u8: np.ndarray,
    kernel_size: int,
    threads_x: int,
    threads_y: int,
    blocks_x: int,
    blocks_y: int,
):
    """
    Ejecuta Sobel en la GPU sobre una imagen en escala de grises (uint8).
    Devuelve (out_u8, tiempo_gpu_ms)
    """
    H, W = gray_u8.shape

    # Generar máscaras Sobel dinámicas
    Kx, Ky = generate_sobel_masks(kernel_size)
    Kx_flat = Kx.astype(np.float32).reshape(-1)
    Ky_flat = Ky.astype(np.float32).reshape(-1)

    mag_gpu = drv.mem_alloc(gray_u8.size * 4)   # float32
    out_gpu = drv.mem_alloc(gray_u8.size)       # uint8

    block = (threads_x, threads_y, 1)
    grid = (blocks_x, blocks_y, 1)

    # Kernel 1
    start_evt = drv.Event()
    end_evt = drv.Event()

    start_evt.record()
    _sobel_mag_gpu(
        drv.In(gray_u8),
        mag_gpu,
        drv.In(Kx_flat),
        drv.In(Ky_flat),
        np.int32(W),
        np.int32(H),
        np.int32(kernel_size),
        block=block, grid=grid
    )
    end_evt.record()
    end_evt.synchronize()
    mag_ms = start_evt.time_till(end_evt)

    # Descargar magnitud para normalizar
    mag_host = np.empty_like(gray_u8, dtype=np.float32)
    drv.memcpy_dtoh(mag_host, mag_gpu)
    max_val = float(mag_host.max())
    if max_val == 0:
        max_val = 1.0

    # Kernel 2: normalizar
    start_evt = drv.Event()
    end_evt = drv.Event()

    start_evt.record()
    _normalize_gpu(
        drv.In(mag_host),
        out_gpu,
        np.float32(max_val),
        np.int32(W),
        np.int32(H),
        block=block, grid=grid
    )
    end_evt.record()
    end_evt.synchronize()
    norm_ms = start_evt.time_till(end_evt)

    out_host = np.empty_like(gray_u8)
    drv.memcpy_dtoh(out_host, out_gpu)

    total_ms = mag_ms + norm_ms
    return out_host, total_ms


def run_mean_gray_gpu(
    gray_u8: np.ndarray,
    kernel_size: int,
    threads_x: int,
    threads_y: int,
    blocks_x: int,
    blocks_y: int,
):
    """
    Filtro promedio NxN en escala de grises.
    """
    gray_u8 = np.ascontiguousarray(gray_u8.astype(np.uint8))
    H, W = gray_u8.shape

    d_in = drv.mem_alloc(gray_u8.nbytes)
    d_out = drv.mem_alloc(gray_u8.nbytes)

    drv.memcpy_htod(d_in, gray_u8)

    block = (threads_x, threads_y, 1)
    grid = (blocks_x, blocks_y, 1)

    start_evt = drv.Event()
    end_evt = drv.Event()

    start_evt.record()
    _mean_filter_gpu(
        d_in,
        d_out,
        np.int32(W),
        np.int32(H),
        np.int32(kernel_size),
        block=block,
        grid=grid
    )
    end_evt.record()
    end_evt.synchronize()
    gpu_ms = start_evt.time_till(end_evt)

    out_u8 = np.empty_like(gray_u8)
    drv.memcpy_dtoh(out_u8, d_out)

    return out_u8, gpu_ms


def run_mean_rgb_gpu(
    img_rgb_u8: np.ndarray,
    kernel_size: int,
    threads_x: int,
    threads_y: int,
    blocks_x: int,
    blocks_y: int,
):
    """
    Aplica el filtro promedio NxN a cada canal R, G, B.
    """
    if img_rgb_u8.ndim != 3 or img_rgb_u8.shape[2] != 3:
        raise ValueError("Imagen debe ser RGB (H, W, 3).")

    H, W, _ = img_rgb_u8.shape
    out_rgb = np.empty_like(img_rgb_u8, dtype=np.uint8)

    tiempos = []

    for c in range(3):
        canal = img_rgb_u8[:, :, c]
        out_c, t_ms = run_mean_gray_gpu(
            gray_u8=canal,
            kernel_size=kernel_size,
            threads_x=threads_x,
            threads_y=threads_y,
            blocks_x=blocks_x,
            blocks_y=blocks_y,
        )
        out_rgb[:, :, c] = out_c
        tiempos.append(t_ms)

    gpu_time_ms = float(sum(tiempos) / len(tiempos))
    return out_rgb, gpu_time_ms


# -------------------------------------------------------------------
#  Convolución genérica (gauss / emboss)
# -------------------------------------------------------------------
CONV_KERNEL_CODE = r"""
extern "C"
__global__ void convolve_gray(
    const float *inbuf,
    float *outbuf,
    const float *K,
    int w,
    int h,
    int ksize
){
    int x = blockIdx.x * blockDim.x + threadIdx.x;
    int y = blockIdx.y * blockDim.y + threadIdx.y;
    if (x >= w || y >= h) return;

    int r = ksize / 2;
    float acc = 0.0f;

    for (int ky = -r; ky <= r; ++ky) {
        int yy = y + ky;
        if (yy < 0) yy = 0;
        else if (yy >= h) yy = h - 1;

        int krow = (ky + r) * ksize;
        int row_off = yy * w;

        for (int kx = -r; kx <= r; ++kx) {
            int xx = x + kx;
            if (xx < 0) xx = 0;
            else if (xx >= w) xx = w - 1;

            float val = inbuf[row_off + xx];
            float kval = K[krow + (kx + r)];
            acc += val * kval;
        }
    }

    outbuf[y * w + x] = acc;
}
"""

_conv_mod = SourceModule(CONV_KERNEL_CODE)
_convolve_gray_gpu = _conv_mod.get_function("convolve_gray")


def run_convolution_gray_gpu(
    gray_f32: np.ndarray,
    kernel_f32: np.ndarray,
    threads_x: int,
    threads_y: int,
    blocks_x: int,
    blocks_y: int,
):
    gray_f32 = np.ascontiguousarray(gray_f32.astype(np.float32))
    kernel_f32 = np.ascontiguousarray(kernel_f32.astype(np.float32))

    h, w = gray_f32.shape

    k = kernel_f32.reshape(-1)
    ksize = kernel_f32.shape[0]

    d_in = drv.mem_alloc(gray_f32.nbytes)
    d_out = drv.mem_alloc(gray_f32.nbytes)
    d_k = drv.mem_alloc(k.nbytes)

    drv.memcpy_htod(d_in, gray_f32)
    drv.memcpy_htod(d_k, k)

    block = (threads_x, threads_y, 1)
    grid = (blocks_x, blocks_y, 1)

    start_evt = drv.Event()
    end_evt = drv.Event()

    start_evt.record()
    _convolve_gray_gpu(
        d_in,
        d_out,
        d_k,
        np.int32(w),
        np.int32(h),
        np.int32(ksize),
        block=block,
        grid=grid,
    )
    end_evt.record()
    end_evt.synchronize()
    kernel_ms = start_evt.time_till(end_evt)

    out_f32 = np.empty_like(gray_f32)
    drv.memcpy_dtoh(out_f32, d_out)

    return out_f32, kernel_ms


def run_convolution_rgb_gpu(
    img_rgb_f32: np.ndarray,
    kernel_f32: np.ndarray,
    threads_x: int,
    threads_y: int,
    blocks_x: int,
    blocks_y: int,
):
    if img_rgb_f32.ndim != 3 or img_rgb_f32.shape[2] < 3:
        raise ValueError("Se esperaba una imagen RGB (H, W, 3).")

    h, w, _ = img_rgb_f32.shape
    out_rgb = np.empty_like(img_rgb_f32, dtype=np.float32)

    tiempos = []
    for c in range(3):
        canal = img_rgb_f32[:, :, c]
        out_c, t_ms = run_convolution_gray_gpu(
            gray_f32=canal,
            kernel_f32=kernel_f32,
            threads_x=threads_x,
            threads_y=threads_y,
            blocks_x=blocks_x,
            blocks_y=blocks_y,
        )
        out_rgb[:, :, c] = out_c
        tiempos.append(t_ms)

    gpu_time_ms = float(sum(tiempos) / len(tiempos))
    return out_rgb, gpu_time_ms
