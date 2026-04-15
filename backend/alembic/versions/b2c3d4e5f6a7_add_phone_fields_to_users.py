"""add phone fields to users

Revision ID: b2c3d4e5f6a7
Revises: a1b2c3d4e5f6
Create Date: 2026-04-16 01:40:00

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "b2c3d4e5f6a7"
down_revision: Union[str, Sequence[str], None] = "a1b2c3d4e5f6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("users", sa.Column("phone_model", sa.String(length=100), nullable=True))
    op.add_column("users", sa.Column("phone_uid", sa.String(length=100), nullable=True))


def downgrade() -> None:
    op.drop_column("users", "phone_uid")
    op.drop_column("users", "phone_model")
