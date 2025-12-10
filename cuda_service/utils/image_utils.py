# app/utils/image_utils.py

from fastapi import UploadFile
from io import BytesIO
from PIL import Image
import numpy as np


async def load_image_to_array(
    file: UploadFile,
    color_mode: str = "grayscale"
) -> tuple[np.ndarray, int, int]:
    """
    Lee un UploadFile y lo convierte a un arreglo NumPy.
    Devuelve: (array, width, height)
    """
    contents = await file.read()
    image = Image.open(BytesIO(contents))

    if color_mode == "grayscale":
        image = image.convert("L")
    elif color_mode == "rgb":
        image = image.convert("RGB")
    else:
        raise ValueError(f"color_mode no soportado: {color_mode}")

    arr = np.array(image, dtype=np.float32)
    height, width = arr.shape[:2]

    return arr, width, height
