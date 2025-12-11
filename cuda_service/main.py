# cuda_service/main.py
import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .routers import convolution

app = FastAPI(
    title="GPU Convolution API",
    version="1.0.0"
)

origins = [
    "http://127.0.0.1:5500",
    "http://localhost:5500",
    "*",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=[
        "X-Width",
        "X-Height",
        "X-Filter-Type",
        "X-Kernel-Size",
        "X-GPU-Time-ms",
    ],
)

app.include_router(convolution.router)

if __name__ == "__main__":
    import uvicorn

    port = int(os.getenv("PORT", "5000"))
    uvicorn.run("main:app", host="0.0.0.0", port=port)
