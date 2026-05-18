"""
AWS RDS battery_sessions + battery_telemetry + devices 증분 fetch 스크립트.

실행:
    .venv/bin/python scripts/fetch_rds_sessions.py
출력:
    data/raw/real_observations/raw_sessions.parquet   -- 세션 메타데이터
    data/raw/real_observations/raw_telemetry.parquet  -- 텔레메트리
    data/raw/real_observations/raw_devices.parquet    -- 기기/파워뱅크 정격 용량
    data/raw/real_observations/manifest.json          -- fetch 이력

환경 변수 (.env):
    RDS_HOST, RDS_PORT, RDS_DB, RDS_USER, RDS_PASSWORD
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd
import psycopg2
from dotenv import load_dotenv

AI_ROOT = Path(__file__).resolve().parents[1]
load_dotenv(AI_ROOT / ".env")
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

OUT_DIR = AI_ROOT / "data" / "raw" / "real_observations"
SESSIONS_PARQUET = OUT_DIR / "raw_sessions.parquet"
TELEMETRY_PARQUET = OUT_DIR / "raw_telemetry.parquet"
DEVICES_PARQUET = OUT_DIR / "raw_devices.parquet"
MANIFEST_PATH = OUT_DIR / "manifest.json"

FETCH_SINCE = "2026-05-04 00:00:00"  # 이 시각 이후 created_at 세션만 대상


def _connect() -> psycopg2.extensions.connection:
    return psycopg2.connect(
        host=os.environ["RDS_HOST"],
        port=int(os.environ.get("RDS_PORT", 5432)),
        dbname=os.environ["RDS_DB"],
        user=os.environ["RDS_USER"],
        password=os.environ["RDS_PASSWORD"],
        connect_timeout=10,
    )


def _query(conn: psycopg2.extensions.connection, sql: str, params: tuple = ()) -> pd.DataFrame:
    cur = conn.cursor()
    cur.execute(sql, params)
    columns = [desc[0] for desc in cur.description]
    return pd.DataFrame(cur.fetchall(), columns=columns)


def _load_manifest() -> dict:
    if MANIFEST_PATH.exists():
        with open(MANIFEST_PATH) as f:
            return json.load(f)
    return {"fetched_session_ids": [], "last_fetched_at": None}


def _save_manifest(manifest: dict) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest["last_fetched_at"] = datetime.now(timezone.utc).isoformat()
    with open(MANIFEST_PATH, "w") as f:
        json.dump(manifest, f, indent=2)


def fetch_new_sessions(
    conn: psycopg2.extensions.connection,
    already_fetched: list[int],
) -> pd.DataFrame:
    """FETCH_SINCE 이후 생성된 세션 중 아직 fetch하지 않은 것만 반환."""
    exclude_ids = tuple(already_fetched) if already_fetched else (-1,)
    return _query(conn, f"""
        SELECT
            id,
            device_id,
            user_id,
            android_api_level,
            session_start_ts,
            session_end_ts,
            capacity_ah,
            label_capacity_ah,
            status,
            powerbank_capacity_start_mah,
            created_at
        FROM battery_sessions
        WHERE created_at >= %s
          AND id NOT IN %s
        ORDER BY created_at ASC
    """, (FETCH_SINCE, exclude_ids))


def fetch_devices(conn: psycopg2.extensions.connection) -> pd.DataFrame:
    """devices 테이블 전체 fetch (파워뱅크 정격 용량 포함)."""
    return _query(conn, "SELECT * FROM devices ORDER BY id ASC")


def fetch_telemetry(
    conn: psycopg2.extensions.connection,
    session_id: int,
) -> pd.DataFrame:
    return _query(conn, """
        SELECT
            session_id,
            timestamp,
            elapsed_ms,
            soc,
            voltage,
            current_ma,
            temperature_c,
            battery_status,
            power_w,
            created_at
        FROM battery_telemetry
        WHERE session_id = %s
        ORDER BY timestamp ASC
    """, (session_id,))


def main() -> None:
    parser = argparse.ArgumentParser(description="RDS 세션 증분 fetch 및 raw parquet 저장")
    parser.add_argument("--dry-run", action="store_true", help="저장 없이 결과만 출력")
    args = parser.parse_args()

    for var in ("RDS_HOST", "RDS_DB", "RDS_USER", "RDS_PASSWORD"):
        if not os.environ.get(var):
            print(f"오류: 환경 변수 {var} 가 설정되지 않았습니다.")
            sys.exit(1)

    manifest = _load_manifest()
    already_fetched: list[int] = manifest["fetched_session_ids"]

    print(f"RDS 접속 중: {os.environ['RDS_HOST']}")
    print(f"기준 일자: {FETCH_SINCE} 이후 생성 세션")
    print(f"이미 fetch된 세션 수: {len(already_fetched)}개")

    conn = _connect()
    try:
        new_sessions = fetch_new_sessions(conn, already_fetched)
        devices = fetch_devices(conn)
    finally:
        conn.close()

    if new_sessions.empty:
        print("새로운 세션 없음")
        return

    print(f"새 세션 {len(new_sessions)}개 발견")

    conn = _connect()
    telemetry_frames: list[pd.DataFrame] = []
    fetched_ids: list[int] = []

    try:
        for _, session in new_sessions.iterrows():
            sid = int(session["id"])
            tel = fetch_telemetry(conn, sid)

            rows = len(tel)
            print(f"  session_id={sid:4d}  device_id={session['device_id']}  rows={rows:5d}  status={session['status']}")

            if not tel.empty:
                telemetry_frames.append(tel)

            fetched_ids.append(sid)
    finally:
        conn.close()

    total_rows = sum(len(f) for f in telemetry_frames)

    if args.dry_run:
        print("\n[dry-run] 저장 생략")
        print(f"fetch 결과: {len(fetched_ids)}개 세션, {total_rows}행")
        print(f"devices: {len(devices)}개")
        return

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # sessions 저장
    new_sessions_df = new_sessions[new_sessions["id"].isin(fetched_ids)].copy()
    if SESSIONS_PARQUET.exists():
        existing_sessions = pd.read_parquet(SESSIONS_PARQUET)
        combined_sessions = pd.concat([existing_sessions, new_sessions_df], ignore_index=True)
    else:
        combined_sessions = new_sessions_df
    combined_sessions.to_parquet(SESSIONS_PARQUET, index=False)

    # telemetry 저장
    if telemetry_frames:
        new_tel_df = pd.concat(telemetry_frames, ignore_index=True)
        if TELEMETRY_PARQUET.exists():
            existing_tel = pd.read_parquet(TELEMETRY_PARQUET)
            combined_tel = pd.concat([existing_tel, new_tel_df], ignore_index=True)
        else:
            combined_tel = new_tel_df
        combined_tel.to_parquet(TELEMETRY_PARQUET, index=False)
        tel_rows = len(combined_tel)
    else:
        tel_rows = 0

    # devices 저장 (매번 전체 덮어쓰기)
    devices.to_parquet(DEVICES_PARQUET, index=False)

    manifest["fetched_session_ids"].extend(fetched_ids)
    _save_manifest(manifest)

    print(f"\n저장 완료: {OUT_DIR}")
    print(f"  raw_sessions.parquet  : {len(combined_sessions)}개 세션")
    print(f"  raw_telemetry.parquet : {tel_rows}행")
    print(f"  raw_devices.parquet   : {len(devices)}개 기기")


if __name__ == "__main__":
    main()
