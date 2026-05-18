"""
LSTM 추론 end-to-end 테스트

실제 체크포인트(artifacts/checkpoints/lstm/nasa_calce/v3/nc_v3_s1_cosine_ep30)를
로드해 추론 파이프라인 전체를 검증한다.

테스트 범위:
  - 체크포인트 로드 (load_lstm_predictor)
  - 단일 세션 예측 (LstmSOHPredictor.predict)
  - 다중 세션 예측 (LstmSOHPredictor.predict_multi)
  - 짧은 세션 ValueError 처리
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from soh_service.core.config import LSTM_CHECKPOINT_DIR
from soh_service.inference.predictor import TelemetryPoint, load_lstm_predictor


def _make_charging_telemetry(
    duration_s: float = 1200.0,
    sample_interval_s: float = 30.0,
) -> list[TelemetryPoint]:
    """단순 선형 충전 곡선 텔레메트리 생성 (테스트용)."""
    n = max(2, int(duration_s / sample_interval_s) + 1)
    points = []
    for i in range(n):
        t = i * sample_interval_s
        progress = t / duration_s
        points.append(
            TelemetryPoint(
                time_s=t,
                voltage_v=3.7 + 0.5 * progress,
                current_a=1.0 - 0.3 * progress,
                temperature_c=25.0 + 3.0 * progress,
            )
        )
    return points


CHECKPOINT_DIR = Path(LSTM_CHECKPOINT_DIR)
_SKIP_REASON = f"체크포인트 없음: {CHECKPOINT_DIR}"
_checkpoint_available = CHECKPOINT_DIR.is_dir() and (CHECKPOINT_DIR / "best.pt").exists()


@unittest.skipUnless(_checkpoint_available, _SKIP_REASON)
class LstmInferenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.predictor = load_lstm_predictor(CHECKPOINT_DIR)

    def test_predict_returns_float_in_range(self) -> None:
        telemetry = _make_charging_telemetry(duration_s=1200.0)
        soh = self.predictor.predict(telemetry)
        self.assertIsInstance(soh, float)
        self.assertGreaterEqual(soh, 0.0)
        self.assertLessEqual(soh, 1.0)

    def test_predict_short_session_raises_value_error(self) -> None:
        telemetry = _make_charging_telemetry(duration_s=300.0)
        with self.assertRaises(ValueError):
            self.predictor.predict(telemetry)

    def test_predict_multi_returns_tuple_and_counts_sessions(self) -> None:
        sessions = [
            _make_charging_telemetry(duration_s=1200.0),
            _make_charging_telemetry(duration_s=1500.0),
        ]
        soh, sessions_used = self.predictor.predict_multi(sessions)
        self.assertIsInstance(soh, float)
        self.assertGreaterEqual(soh, 0.0)
        self.assertLessEqual(soh, 1.0)
        self.assertEqual(sessions_used, 2)

    def test_predict_multi_skips_short_sessions(self) -> None:
        sessions = [
            _make_charging_telemetry(duration_s=300.0),  # 짧아서 제외됨
            _make_charging_telemetry(duration_s=1200.0),
        ]
        soh, sessions_used = self.predictor.predict_multi(sessions)
        self.assertEqual(sessions_used, 1)
        self.assertGreaterEqual(soh, 0.0)
        self.assertLessEqual(soh, 1.0)

    def test_predict_multi_all_short_raises_value_error(self) -> None:
        sessions = [
            _make_charging_telemetry(duration_s=300.0),
            _make_charging_telemetry(duration_s=400.0),
        ]
        with self.assertRaises(ValueError):
            self.predictor.predict_multi(sessions)

    def test_predict_without_temperature(self) -> None:
        telemetry = [
            TelemetryPoint(time_s=t * 30.0, voltage_v=3.7 + 0.5 * (t / 40), current_a=1.0)
            for t in range(41)
        ]
        soh = self.predictor.predict(telemetry)
        self.assertGreaterEqual(soh, 0.0)
        self.assertLessEqual(soh, 1.0)



if __name__ == "__main__":
    unittest.main()
