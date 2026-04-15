"""drop manufacturer from devices

Revision ID: a1b2c3d4e5f6
Revises: fd449f94d1b0
Create Date: 2026-04-15 22:10:00

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "a1b2c3d4e5f6"
down_revision: Union[str, Sequence[str], None] = "fd449f94d1b0"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.drop_column("devices", "manufacturer")


def downgrade() -> None:
    op.add_column(
        "devices",
        sa.Column("manufacturer", sa.String(length=100), nullable=True),
    )
