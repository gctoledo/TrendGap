from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()


class Health(BaseModel):
    status: Literal["UP"]


@router.get("/health", operation_id="getHealth", tags=["health"])
def get_health() -> Health:
    return Health(status="UP")
