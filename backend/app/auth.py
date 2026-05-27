import hashlib
import secrets
from datetime import datetime, timedelta

from fastapi import Depends, Header, HTTPException
from sqlalchemy.orm import Session

from .database import get_db
from .models import AuthSession, User


SESSION_DAYS = 7
DEFAULT_USERS = [
    ("admin_demo", "Admin12345!", "管理员", "admin"),
    ("reviewer_demo", "Review12345!", "复核员", "reviewer"),
    ("user_demo", "User12345!", "普通用户", "user"),
]


def hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    iterations = 200_000
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), iterations).hex()
    return f"pbkdf2_sha256${iterations}${salt}${digest}"


def verify_password(password: str, password_hash: str) -> bool:
    try:
        algorithm, iterations, salt, expected = password_hash.split("$", 3)
    except ValueError:
        return False
    if algorithm != "pbkdf2_sha256":
        return False
    digest = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt.encode("utf-8"), int(iterations)
    ).hex()
    return secrets.compare_digest(digest, expected)


def token_digest(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def public_user(user: User) -> dict:
    return {
        "id": user.id,
        "username": user.username,
        "display_name": user.display_name,
        "role": user.role,
    }


def create_session(db: Session, user: User) -> str:
    token = secrets.token_urlsafe(32)
    db.add(
        AuthSession(
            user_id=user.id,
            token_hash=token_digest(token),
            expires_at=datetime.utcnow() + timedelta(days=SESSION_DAYS),
        )
    )
    db.commit()
    return token


def auth_token(authorization: str | None) -> str | None:
    if not authorization:
        return None
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        return None
    return token


def get_current_user(
    authorization: str | None = Header(default=None), db: Session = Depends(get_db)
) -> User:
    token = auth_token(authorization)
    if not token:
        raise HTTPException(status_code=401, detail="请先登录")
    session = db.query(AuthSession).filter(AuthSession.token_hash == token_digest(token)).first()
    if not session or session.expires_at <= datetime.utcnow():
        raise HTTPException(status_code=401, detail="登录已失效，请重新登录")
    if not session.user or not session.user.is_active:
        raise HTTPException(status_code=401, detail="账号不可用")
    return session.user


def get_optional_user(
    authorization: str | None = Header(default=None), db: Session = Depends(get_db)
) -> User | None:
    token = auth_token(authorization)
    if not token:
        return None
    session = db.query(AuthSession).filter(AuthSession.token_hash == token_digest(token)).first()
    if not session or session.expires_at <= datetime.utcnow() or not session.user or not session.user.is_active:
        return None
    return session.user


def require_roles(*roles: str):
    def dependency(user: User = Depends(get_current_user)) -> User:
        if user.role not in roles:
            raise HTTPException(status_code=403, detail="当前账号没有访问权限")
        return user

    return dependency


def ensure_default_users(db: Session) -> None:
    for username, password, display_name, role in DEFAULT_USERS:
        if db.query(User).filter(User.username == username).first():
            continue
        db.add(
            User(
                username=username,
                display_name=display_name,
                password_hash=hash_password(password),
                role=role,
            )
        )
    db.commit()
