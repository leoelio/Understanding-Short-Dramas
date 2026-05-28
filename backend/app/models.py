from datetime import datetime

from sqlalchemy import Boolean, Column, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship

from .database import Base


class Drama(Base):
    __tablename__ = "dramas"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(255), unique=True, nullable=False)
    genre = Column(String(64), default="未分类")
    description = Column(Text, default="")
    created_at = Column(DateTime, default=datetime.utcnow)

    episodes = relationship("Episode", back_populates="drama", cascade="all, delete-orphan")


class Episode(Base):
    __tablename__ = "episodes"

    id = Column(Integer, primary_key=True, index=True)
    drama_id = Column(Integer, ForeignKey("dramas.id"), nullable=False, index=True)
    episode_no = Column(Integer, nullable=False)
    title = Column(String(255), nullable=False)
    video_path = Column(Text, nullable=False)
    duration_sec = Column(Float, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)

    drama = relationship("Drama", back_populates="episodes")
    highlights = relationship("Highlight", back_populates="episode", cascade="all, delete-orphan")
    experience_config = relationship(
        "EpisodeExperienceConfig", back_populates="episode", cascade="all, delete-orphan", uselist=False
    )
    watch_history = relationship("WatchHistory", back_populates="episode", cascade="all, delete-orphan")


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(64), unique=True, nullable=False, index=True)
    display_name = Column(String(64), nullable=False)
    password_hash = Column(Text, nullable=False)
    role = Column(String(32), default="user", nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    sessions = relationship("AuthSession", back_populates="user", cascade="all, delete-orphan")
    watch_history = relationship("WatchHistory", back_populates="user", cascade="all, delete-orphan")


class AuthSession(Base):
    __tablename__ = "auth_sessions"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    token_hash = Column(String(64), unique=True, nullable=False, index=True)
    expires_at = Column(DateTime, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    user = relationship("User", back_populates="sessions")


class Highlight(Base):
    __tablename__ = "highlights"

    id = Column(Integer, primary_key=True, index=True)
    episode_id = Column(Integer, ForeignKey("episodes.id"), nullable=False, index=True)
    start_time_sec = Column(Float, nullable=False)
    end_time_sec = Column(Float, nullable=False)
    title = Column(String(255), nullable=False)
    description = Column(Text, default="")
    highlight_type = Column(String(64), nullable=False)
    emotion = Column(String(64), nullable=False)
    options_json = Column(Text, nullable=False)
    source = Column(String(64), default="manual_seed")
    confidence = Column(Float, default=0.75)
    model_version = Column(String(64), default="seed-v1")
    annotation_reason = Column(Text, default="")
    evidence_segment_ids_json = Column(Text, default="[]")
    evidence_text = Column(Text, default="")
    created_at = Column(DateTime, default=datetime.utcnow)

    episode = relationship("Episode", back_populates="highlights")
    interactions = relationship("Interaction", back_populates="highlight", cascade="all, delete-orphan")


class Interaction(Base):
    __tablename__ = "interactions"

    id = Column(Integer, primary_key=True, index=True)
    highlight_id = Column(Integer, ForeignKey("highlights.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)
    option_key = Column(String(64), nullable=False)
    session_id = Column(String(128), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    highlight = relationship("Highlight", back_populates="interactions")
    user = relationship("User")


class DanmakuComment(Base):
    __tablename__ = "danmaku_comments"

    id = Column(Integer, primary_key=True, index=True)
    episode_id = Column(Integer, ForeignKey("episodes.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)
    time_sec = Column(Float, nullable=False, index=True)
    text = Column(String(80), nullable=False)
    mode = Column(String(32), default="light")
    session_id = Column(String(128), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    episode = relationship("Episode")
    user = relationship("User")


class WatchHistory(Base):
    __tablename__ = "watch_history"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    episode_id = Column(Integer, ForeignKey("episodes.id"), nullable=False, index=True)
    progress_sec = Column(Float, default=0)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    user = relationship("User", back_populates="watch_history")
    episode = relationship("Episode", back_populates="watch_history")


class WatchRoom(Base):
    __tablename__ = "watch_rooms"

    id = Column(Integer, primary_key=True, index=True)
    code = Column(String(12), unique=True, nullable=False, index=True)
    host_user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    guest_user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)
    episode_id = Column(Integer, ForeignKey("episodes.id"), nullable=True, index=True)
    progress_sec = Column(Float, default=0)
    playback_state = Column(String(16), default="paused")
    updated_by_user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    host = relationship("User", foreign_keys=[host_user_id])
    guest = relationship("User", foreign_keys=[guest_user_id])
    updated_by = relationship("User", foreign_keys=[updated_by_user_id])
    episode = relationship("Episode")


class EpisodeExperienceConfig(Base):
    __tablename__ = "episode_experience_configs"

    id = Column(Integer, primary_key=True, index=True)
    episode_id = Column(Integer, ForeignKey("episodes.id"), nullable=False, unique=True, index=True)
    version = Column(Integer, default=1)
    source = Column(String(64), default="manual")
    model_version = Column(String(64), default="experience-config-v1")
    review_status = Column(String(64), default="draft")
    config_json = Column(Text, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    episode = relationship("Episode", back_populates="experience_config")
