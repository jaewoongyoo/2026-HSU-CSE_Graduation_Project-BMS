"""align battery_sessions to team schema

Revision ID: c3d4e5f6a7b8
Revises: b2c3d4e5f6a7
Create Date: 2026-04-20 16:25:00

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "c3d4e5f6a7b8"
down_revision: Union[str, Sequence[str], None] = "b2c3d4e5f6a7"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def _get_column_names(table_name: str) -> set[str]:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    return {column["name"] for column in inspector.get_columns(table_name)}


def _get_foreign_keys(table_name: str) -> list[dict]:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    return inspector.get_foreign_keys(table_name)


def upgrade() -> None:
    existing_columns = _get_column_names("battery_sessions")

    if "powerbank_capacity_start_mah" not in existing_columns:
        op.add_column(
            "battery_sessions",
            sa.Column("powerbank_capacity_start_mah", sa.Float(), nullable=True),
        )
    if "powerbank_capacity_end_mah" not in existing_columns:
        op.add_column(
            "battery_sessions",
            sa.Column("powerbank_capacity_end_mah", sa.Float(), nullable=True),
        )
    if "label_capacity_ah" not in existing_columns:
        op.add_column(
            "battery_sessions",
            sa.Column("label_capacity_ah", sa.Float(), nullable=True),
        )

    for obsolete_column in ("powerbank_capacity_mah", "cable_id", "phone_capacity_mah"):
        if obsolete_column in existing_columns:
            op.drop_column("battery_sessions", obsolete_column)

    for fk in _get_foreign_keys("battery_sessions"):
        constrained = fk.get("constrained_columns") or []
        if fk.get("name") and constrained in (["device_id"], ["user_id"]):
            op.drop_constraint(fk["name"], "battery_sessions", type_="foreignkey")

    op.execute(
        sa.text(
            """
            ALTER TABLE battery_sessions
            ALTER COLUMN device_id TYPE INTEGER
            USING device_id::integer
            """
        )
    )

    with op.batch_alter_table("battery_sessions") as batch_op:
        batch_op.alter_column("device_id", existing_type=sa.Integer(), nullable=False)
        batch_op.alter_column("user_id", existing_type=sa.Integer(), nullable=False)
        batch_op.create_foreign_key(
            "battery_sessions_device_id_fkey",
            "devices",
            ["device_id"],
            ["id"],
            ondelete="CASCADE",
        )
        batch_op.create_foreign_key(
            "battery_sessions_user_id_fkey",
            "users",
            ["user_id"],
            ["id"],
            ondelete="CASCADE",
        )


def downgrade() -> None:
    existing_columns = _get_column_names("battery_sessions")

    for fk in _get_foreign_keys("battery_sessions"):
        constrained = fk.get("constrained_columns") or []
        if fk.get("name") and constrained in (["device_id"], ["user_id"]):
            op.drop_constraint(fk["name"], "battery_sessions", type_="foreignkey")

    if "powerbank_capacity_mah" not in existing_columns:
        op.add_column(
            "battery_sessions",
            sa.Column("powerbank_capacity_mah", sa.Integer(), nullable=True),
        )

    for added_column in (
        "powerbank_capacity_start_mah",
        "powerbank_capacity_end_mah",
        "label_capacity_ah",
    ):
        if added_column in existing_columns:
            op.drop_column("battery_sessions", added_column)

    with op.batch_alter_table("battery_sessions") as batch_op:
        batch_op.alter_column("device_id", existing_type=sa.Integer(), nullable=True)
        batch_op.alter_column("user_id", existing_type=sa.Integer(), nullable=True)
        batch_op.create_foreign_key(
            "battery_sessions_device_id_fkey",
            "devices",
            ["device_id"],
            ["id"],
            ondelete="CASCADE",
        )
        batch_op.create_foreign_key(
            "battery_sessions_user_id_fkey",
            "users",
            ["user_id"],
            ["id"],
            ondelete="CASCADE",
        )
