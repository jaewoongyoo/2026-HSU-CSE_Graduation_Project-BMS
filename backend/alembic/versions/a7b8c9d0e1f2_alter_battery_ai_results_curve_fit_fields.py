"""alter battery_ai_results for curve_fit fields

곡선 피팅(curve_fit) 서빙 전환에 맞춰 battery_ai_results 스키마 정리:
- 추가: standard_soh_percentage, degradation_rate_ratio, sessions_used,
        sessions_total, confidence (AI 응답의 개인화/신뢰도 필드)
- 제거: smartphone_received_mah (LSTM 시절 잔재, 항상 0.0),
        raw_response_json (개별 컬럼화로 불필요)

Revision ID: a7b8c9d0e1f2
Revises: f6a7b8c9d0e1
Create Date: 2026-06-01 00:00:00

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "a7b8c9d0e1f2"
down_revision: Union[str, Sequence[str], None] = "f6a7b8c9d0e1"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "battery_ai_results",
        sa.Column("standard_soh_percentage", sa.Float(), nullable=True),
    )
    op.add_column(
        "battery_ai_results",
        sa.Column("degradation_rate_ratio", sa.Float(), nullable=True),
    )
    op.add_column(
        "battery_ai_results",
        sa.Column("sessions_used", sa.Integer(), nullable=True),
    )
    op.add_column(
        "battery_ai_results",
        sa.Column("sessions_total", sa.Integer(), nullable=True),
    )
    op.add_column(
        "battery_ai_results",
        sa.Column("confidence", sa.String(length=20), nullable=True),
    )
    op.drop_column("battery_ai_results", "smartphone_received_mah")
    op.drop_column("battery_ai_results", "raw_response_json")


def downgrade() -> None:
    op.add_column(
        "battery_ai_results",
        sa.Column("raw_response_json", sa.Text(), nullable=True),
    )
    op.add_column(
        "battery_ai_results",
        sa.Column("smartphone_received_mah", sa.Float(), nullable=True),
    )
    op.drop_column("battery_ai_results", "confidence")
    op.drop_column("battery_ai_results", "sessions_total")
    op.drop_column("battery_ai_results", "sessions_used")
    op.drop_column("battery_ai_results", "degradation_rate_ratio")
    op.drop_column("battery_ai_results", "standard_soh_percentage")
