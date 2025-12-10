# app/routers/convolution.py

import base64
from fastapi import APIRouter, UploadFile, File, Form
from fastapi.responses import Response
from fastapi.responses import JSONResponse

from cuda_service.services.convolution_service import process_convolution

router = APIRouter(
    prefix="/api",
    tags=["convolution"]
)

@router.post("/convolucion")
async def convolution_endpoint(
    image: UploadFile = File(...),
    filter_type: str = Form(...),
    kernel_size: int = Form(...),
):
    # Endpoint completo (con metadata, kernel_info, etc.)
    result = await process_convolution(
        image=image,
        filter_type=filter_type,
        kernel_size=kernel_size,
    )

    img_b64 = result["result"]["image_base64"]

    img_bytes = base64.b64decode(img_b64)

    headers = {
        "X-Width": str(result["image_info"]["width"]),
        "X-Height": str(result["image_info"]["height"]),
        "X-Filter-Type": result["filter"]["type"],
        "X-Kernel-Size": str(result["filter"]["kernel_size"]),
        "X-GPU-Time-ms": str(result["gpu_info"]["gpu_time_ms"]),
    }
    return Response(content=img_bytes, media_type="image/png", headers=headers)
