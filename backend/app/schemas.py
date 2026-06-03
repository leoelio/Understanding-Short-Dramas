from typing import Any

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


class EpisodeRemixCreate(BaseModel):
    choice_key: str = Field(min_length=1, max_length=64)
    session_id: str = Field(min_length=1, max_length=128)


class EpisodeRemixReviewUpdate(BaseModel):
    review_status: str | None = Field(default=None, max_length=32)
    is_featured: bool | None = None
    review_note: str | None = Field(default=None, max_length=300)
    featured_order: int | None = Field(default=None, ge=0, le=999)
    title: str | None = Field(default=None, min_length=1, max_length=255)
    logline: str | None = Field(default=None, max_length=500)
    emotion: str | None = Field(default=None, max_length=64)
    story_text: str | None = Field(default=None, max_length=1200)
    share_copy: str | None = Field(default=None, max_length=500)
    storyboard: list[dict[str, Any]] | None = None


class ExperienceConfigUpdate(BaseModel):
    version: int = Field(default=1, ge=1)
    source: str = Field(default="human_review", max_length=64)
    model_version: str = Field(default="experience-config-v1", max_length=64)
    review_status: str = Field(default="human_reviewed", max_length=64)
    config: dict[str, Any]


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(min_length=1, max_length=64)


class LoginRequest(BaseModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=128)


class WatchHistoryUpdate(BaseModel):
    episode_id: int
    progress_sec: float = Field(default=0, ge=0)


class UserProfileUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=64)
    avatar_url: str | None = Field(default=None, max_length=500)


class FriendCreate(BaseModel):
    user_id: int


class WatchRoomCreate(BaseModel):
    episode_id: int | None = None
    progress_sec: float = Field(default=0, ge=0)
    playback_state: str = Field(default="paused", max_length=16)


class WatchRoomJoin(BaseModel):
    code: str = Field(min_length=4, max_length=12)


class WatchRoomSync(BaseModel):
    episode_id: int
    progress_sec: float = Field(default=0, ge=0)
    playback_state: str = Field(default="paused", max_length=16)


class WatchRoomEventCreate(BaseModel):
    event_type: str = Field(min_length=1, max_length=32)
    payload: dict[str, Any] = Field(default_factory=dict)


class WatchRoomInvitationCreate(BaseModel):
    user_id: int


class UserAdminUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=64)
    role: str | None = Field(default=None, max_length=32)
    is_active: bool | None = None


class VoiceClipCreate(BaseModel):
    text: str = Field(min_length=1, max_length=180)
    scene_key: str = Field(default="manual_preview", min_length=1, max_length=96)
