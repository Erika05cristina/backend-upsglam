import math
from typing import Optional, Dict, Any

import numpy as np

# ---------------------------------------------------------------------
#  TAMAÑOS PREDEFINIDOS
# ---------------------------------------------------------------------

GAUSSIAN_SIZES = {31, 71, 141}
SOBEL_SIZES = {3, 9, 15}
EMBOSS_SIZES = {9, 21, 65}


def is_preset_size(filter_type: str, kernel_size: int) -> bool:
    """Devuelve True si el tamaño corresponde a uno de los presets."""
    ft = filter_type.lower()
    if ft == "gaussian":
        return kernel_size in GAUSSIAN_SIZES
    if ft == "sobel":
        return kernel_size in SOBEL_SIZES
    if ft == "emboss":
        return kernel_size in EMBOSS_SIZES
    return False

# ---------------------------------------------------------------------
#  SOBEL
# ---------------------------------------------------------------------

def generate_sobel_masks(n: int) -> tuple[np.ndarray, np.ndarray]:
    """
    Genera las máscaras Sobel dinámicas Kx y Ky (float32),
    usando exactamente la fórmula de tu script.
    """
    c = n // 2
    Kx = np.zeros((n, n), dtype=np.float32)
    Ky = np.zeros((n, n), dtype=np.float32)

    for i in range(n):
        for j in range(n):
            Kx[i, j] = (j - c) * (abs(i - c) + 1)
            Ky[i, j] = (i - c) * (abs(j - c) + 1)

    return Kx, Ky

# ---------------------------------------------------------------------
#  GAUSSIANO
# ---------------------------------------------------------------------

def gaussian_kernel_1d(mask_size: int, sigma: Optional[float] = None) -> tuple[np.ndarray, int]:
    """
    Genera un kernel gaussiano 1D normalizado (float32) y devuelve
    (kernel_1d, radio), copiando la lógica de tu código.
    """
    if mask_size < 3:
        mask_size = 3
    if mask_size % 2 == 0:
        mask_size += 1

    r = (mask_size - 1) // 2

    # Si sigma no se pasa o es <= 0, usar la heurística de tu script
    if sigma is None or sigma <= 0:
        sigma = (r / 3.0) if r > 0 else 0.8

    two_sigma2 = 2.0 * sigma * sigma

    vals = []
    s = 0.0
    for i in range(-r, r + 1):
        v = math.exp(-(i * i) / two_sigma2)
        vals.append(v)
        s += v

    vals = [v / s for v in vals]  # normalizar
    k1d = np.array(vals, dtype=np.float32)

    return k1d, r


def generate_gaussian_2d(mask_size: int, sigma: Optional[float] = None) -> np.ndarray:
    """
    Usa el kernel 1D gaussiano para generar un kernel 2D
    como producto externo (equivalente a tu versión separable).
    """
    k1d, _ = gaussian_kernel_1d(mask_size, sigma)
    k2d = np.outer(k1d, k1d).astype(np.float32)
    # ya queda normalizado porque k1d está normalizado
    return k2d

# ---------------------------------------------------------------------
#  EMBOSS
# ---------------------------------------------------------------------

def generate_emboss_kernel(k: int) -> np.ndarray:
    """
    Kernel kxk estilo C++ de tu código:
      -1 por encima de la diagonal principal (suma de índices < k-1),
      +1 por debajo, 0 en la diagonal.
    Escala por 1/sqrt(nonZero).
    """
    K = [0.0] * (k * k)
    diag = k - 1
    non_zero = 0

    for i in range(k):
        for j in range(k):
            s = i + j
            idx = i * k + j
            if s < diag:
                K[idx] = -1.0
                non_zero += 1
            elif s > diag:
                K[idx] = +1.0
                non_zero += 1
            else:
                K[idx] = 0.0

    if non_zero > 0:
        scale = 1.0 / math.sqrt(non_zero)
        for i in range(k * k):
            K[i] *= scale

    return np.array(K, dtype=np.float32).reshape((k, k))


# ---------------------------------------------------------------------
#  VALIDACIONES
# ---------------------------------------------------------------------

def validate_kernel_size_generic(kernel_size: int) -> None:
    """
    Valida que el tamaño sea impar y >= 3.
    Esto aplica tanto para presets como para tamaños personalizados.
    """
    if kernel_size < 3 or kernel_size % 2 == 0:
        raise ValueError("kernel_size debe ser un entero impar y >= 3.")

def validate_kernel_vs_image(kernel_size: int, width: int, height: int) -> None:
    """
    Verifica que el kernel quepa en la imagen.
    """
    min_dim = min(width, height)
    if kernel_size > min_dim:
        raise ValueError(
            f"El kernel {kernel_size}x{kernel_size} es demasiado grande "
            f"para la imagen de {width}x{height}. Debe ser <= {min_dim}."
        )

# ---------------------------------------------------------------------
#  FUNCIÓN PRINCIPAL
# ---------------------------------------------------------------------

def build_kernel(filter_type: str, kernel_size: int, allow_custom: bool = True) -> Dict[str, Any]:
    """
    Construye la máscara de convolución para el filtro indicado.

    - filter_type: 'gaussian' | 'sobel' | 'emboss'
    - kernel_size: puede ser uno de los presets (31,71,141 etc.)
                   o cualquier impar >=3 si allow_custom=True.
    - allow_custom: si es False, solo acepta los tamaños de los presets.

    Devuelve:
      - Para sobel: { "Kx": Kx, "Ky": Ky, "is_preset": bool }
      - Para gauss / emboss: { "K": K, "is_preset": bool }
    """
    filter_type = filter_type.lower()

    if filter_type not in {"gaussian", "sobel", "emboss"}:
        raise ValueError("filter_type debe ser 'gaussian', 'sobel' o 'emboss'.")

    # 1) validar tamaño básico (impar y >=3)
    validate_kernel_size_generic(kernel_size)

    # 2) saber si es preset o personalizado
    preset = is_preset_size(filter_type, kernel_size)

    if not preset and not allow_custom:
        raise ValueError(
            f"kernel_size {kernel_size} no está dentro de los tamaños "
            f"predefinidos para {filter_type}."
        )

    # 3) construir kernel real
    if filter_type == "sobel":
        Kx, Ky = generate_sobel_masks(kernel_size)
        return {"Kx": Kx, "Ky": Ky, "is_preset": preset}

    if filter_type == "gaussian":
        K = generate_gaussian_2d(kernel_size)
        return {"K": K, "is_preset": preset}

    if filter_type == "emboss":
        K = generate_emboss_kernel(kernel_size)
        return {"K": K, "is_preset": preset}

    # no debería llegar aquí
    raise ValueError(f"Filtro no soportado: {filter_type}")