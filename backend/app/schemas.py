from pydantic import BaseModel, Field


class InteractionCreate(BaseModel):
    highlight_id: int
    option_key: str = Field(min_length=1, max_length=64)
    session_id: str = Field(min_length=1, max_length=128)


class DanmakuCreate(BaseModel):
    episode_id: int
    time_sec: float = Field(ge=0)
    text: str = Field(min_length=1, max_length=40)
    session_id: str = Field(min_length=1, max_length=128)
    mode: str = Field(default="light", max_length=32)
