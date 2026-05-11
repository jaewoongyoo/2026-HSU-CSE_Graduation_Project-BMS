"""allow deferred session details

Revision ID: d4e5f6a7b8c9
Revises: c3d4e5f6a7b8
Create Date: 2026-04-20 18:10:00

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "d4e5f6a7b8c9"
down_revision: Union[str, Sequence[str], None] = "c3d4e5f6a7b8"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table("battery_sessions") as batch_op:
        batch_op.alter_column("android_api_level", existing_type=sa.Integer(), nullable=True)
        batch_op.alter_column("session_start_ts", existing_type=sa.DateTime(), nullable=True)


def downgrade() -> None:
    op.execute(
        sa.text(
            """
            UPDATE battery_sessions
            SET android_api_level = 0
            WHERE android_api_level IS NULL
            """
        )
    )
    op.execute(
        sa.text(
            """
            UPDATE battery_sessions
            SET session_start_ts = created_at
            WHERE session_start_ts IS NULL
            """
        )
    )

    with op.batch_alter_table("battery_sessions") as batch_op:
        batch_op.alter_column("android_api_level", existing_type=sa.Integer(), nullable=False)
        batch_op.alter_column("session_start_ts", existing_type=sa.DateTime(), nullable=False)
