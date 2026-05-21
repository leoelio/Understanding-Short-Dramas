from pydantic import BaseModel, Field


class InteractionCreate(BaseModel):
    highlight_id: int
    option_key: str = Field(min_length=1, max_length=64)
    session_id: str = Field(min_length=1, max_length=128)

