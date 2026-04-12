from __future__ import annotations

from dataclasses import dataclass

import torch
from torch import nn
from torch.nn.utils.rnn import pack_padded_sequence


@dataclass(frozen=True)
class HierarchicalLstmConfig:
    input_size: int
    step_hidden_size: int = 64
    bundle_hidden_size: int = 64
    step_layers: int = 1
    bundle_layers: int = 1
    dropout: float = 0.1


class HierarchicalLstmRegressor(nn.Module):
    def __init__(self, config: HierarchicalLstmConfig) -> None:
        super().__init__()
        step_dropout = config.dropout if config.step_layers > 1 else 0.0
        bundle_dropout = config.dropout if config.bundle_layers > 1 else 0.0

        self.step_encoder = nn.LSTM(
            input_size=config.input_size,
            hidden_size=config.step_hidden_size,
            num_layers=config.step_layers,
            batch_first=True,
            dropout=step_dropout,
        )
        self.bundle_encoder = nn.LSTM(
            input_size=config.step_hidden_size,
            hidden_size=config.bundle_hidden_size,
            num_layers=config.bundle_layers,
            batch_first=True,
            dropout=bundle_dropout,
        )
        self.regression_head = nn.Sequential(
            nn.LayerNorm(config.bundle_hidden_size),
            nn.Linear(config.bundle_hidden_size, 1),
        )

    def forward(
        self,
        inputs: torch.Tensor,
        window_mask: torch.Tensor,
    ) -> torch.Tensor:
        if inputs.ndim != 4:
            raise ValueError("inputs must have shape [batch, windows, steps, features]")
        if window_mask.ndim != 2:
            raise ValueError("window_mask must have shape [batch, windows]")

        batch_size, max_windows, step_count, feature_count = inputs.shape
        flattened_inputs = inputs.reshape(batch_size * max_windows, step_count, feature_count)
        step_output, _ = self.step_encoder(flattened_inputs)
        step_embeddings = step_output[:, -1, :].reshape(batch_size, max_windows, -1)

        window_lengths = window_mask.sum(dim=1).long()
        if torch.any(window_lengths <= 0):
            raise ValueError("Each batch element must contain at least one valid window")

        packed_embeddings = pack_padded_sequence(
            step_embeddings,
            lengths=window_lengths.cpu(),
            batch_first=True,
            enforce_sorted=False,
        )
        _, (bundle_hidden, _bundle_cell) = self.bundle_encoder(packed_embeddings)
        bundle_embeddings = bundle_hidden[-1]
        predictions = self.regression_head(bundle_embeddings).squeeze(-1)
        return predictions
