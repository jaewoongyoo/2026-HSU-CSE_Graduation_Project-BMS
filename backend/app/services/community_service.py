import logging
from typing import Optional

from sqlalchemy.orm import Session

from app.repositories.postgres_repo import (
    create_shared_report,
    delete_shared_report,
    get_all_public_shared_reports,
    get_distinct_capacities,
    get_distinct_manufacturers,
    get_distinct_phone_models,
    get_latest_ai_result_for_device,
    get_latest_soh_analysis_for_device,
    get_public_shared_reports_by_filter,
    get_session_stats_for_device,
    update_shared_report,
)
from app.schemas.community import (
    CommunityCardResponse,
    CommunityFilterRequest,
    CommunityShareRequest,
    CommunityShareResponse,
    DeviceInfo,
    EfficiencyStats,
    SessionStats,
    UserInfo,
)

logger = logging.getLogger(__name__)


def _build_community_card(shared_report, device, user) -> CommunityCardResponse:
    """공유 보고서에서 커뮤니티 카드 응답 생성"""
    # 세션 통계
    session_stats_dict = get_session_stats_for_device(None, device.id)  # DB session 필요

    # 효율 및 SOH 통계
    ai_result = None
    soh_analysis = None
    efficiency_pct = None
    mean_temp = None

    # AI 결과에서 효율 계산
    if ai_result and ai_result.powerbank_usable_mah and ai_result.smartphone_received_mah:
        efficiency_pct = round(
            (ai_result.smartphone_received_mah / ai_result.powerbank_usable_mah) * 100, 2
        )
        mean_temp = ai_result.mean_temperature_c

    # SOH 찾기
    soh_percentage = None
    if ai_result and ai_result.soh_percentage:
        soh_percentage = ai_result.soh_percentage
    elif soh_analysis and soh_analysis.current_soh:
        soh_percentage = soh_analysis.current_soh

    return CommunityCardResponse(
        shared_report_id=shared_report.id,
        user=UserInfo(
            username=user.username,
            phone_model=user.phone_model,
        ),
        device=DeviceInfo(
            manufacturer=device.manufacturer,
            model_name=device.model_name,
            powerbank_capacity_mah=device.powerbank_capacity_mah,
        ),
        session_stats=SessionStats(**session_stats_dict),
        efficiency_stats=EfficiencyStats(
            soh_percentage=soh_percentage,
            efficiency_pct=efficiency_pct,
            mean_temperature_c=mean_temp,
        ),
        created_at=shared_report.created_at,
    )


def get_community_feed(
    db: Session,
    limit: int = 50,
    offset: int = 0,
) -> list[CommunityCardResponse]:
    """전체 커뮤니티 피드 조회"""
    results = get_all_public_shared_reports(db, limit=limit, offset=offset)
    cards = []
    for shared_report, device, user in results:
        # 각 카드를 위해 DB 조회 (session_stats, ai_result 등)
        session_stats = get_session_stats_for_device(db, device.id)
        ai_result = get_latest_ai_result_for_device(db, device.id)
        soh_analysis = get_latest_soh_analysis_for_device(db, device.id)

        # 효율 계산
        efficiency_pct = None
        mean_temp = None
        if ai_result and ai_result.powerbank_usable_mah and ai_result.smartphone_received_mah:
            efficiency_pct = round(
                (ai_result.smartphone_received_mah / ai_result.powerbank_usable_mah) * 100, 2
            )
            mean_temp = ai_result.mean_temperature_c

        # SOH 찾기
        soh_percentage = None
        if ai_result and ai_result.soh_percentage:
            soh_percentage = ai_result.soh_percentage
        elif soh_analysis and soh_analysis.current_soh:
            soh_percentage = soh_analysis.current_soh

        card = CommunityCardResponse(
            shared_report_id=shared_report.id,
            user=UserInfo(
                username=user.username,
                phone_model=user.phone_model,
            ),
            device=DeviceInfo(
                manufacturer=device.manufacturer,
                model_name=device.model_name,
                powerbank_capacity_mah=device.powerbank_capacity_mah,
            ),
            session_stats=SessionStats(**session_stats),
            efficiency_stats=EfficiencyStats(
                soh_percentage=soh_percentage,
                efficiency_pct=efficiency_pct,
                mean_temperature_c=mean_temp,
            ),
            created_at=shared_report.created_at,
        )
        cards.append(card)

    return cards


def get_community_feed_by_filter(
    db: Session,
    request: CommunityFilterRequest,
) -> list[CommunityCardResponse]:
    """필터링된 커뮤니티 피드 조회"""
    results = get_public_shared_reports_by_filter(
        db,
        phone_models=request.phone_models,
        manufacturers=request.manufacturers,
<<<<<<< Updated upstream
        capacities=request.capacities,
=======
        powerbank_capacity_min=request.powerbank_capacity_min,
        powerbank_capacity_max=request.powerbank_capacity_max,
>>>>>>> Stashed changes
        soh_min=request.soh_min,
        soh_max=request.soh_max,
        limit=request.limit,
        offset=request.offset,
    )

    cards = []
    for shared_report, device, user in results:
        session_stats = get_session_stats_for_device(db, device.id)
        ai_result = get_latest_ai_result_for_device(db, device.id)
        soh_analysis = get_latest_soh_analysis_for_device(db, device.id)

        efficiency_pct = None
        mean_temp = None
        if ai_result and ai_result.powerbank_usable_mah and ai_result.smartphone_received_mah:
            efficiency_pct = round(
                (ai_result.smartphone_received_mah / ai_result.powerbank_usable_mah) * 100, 2
            )
            mean_temp = ai_result.mean_temperature_c

        soh_percentage = None
        if ai_result and ai_result.soh_percentage:
            soh_percentage = ai_result.soh_percentage
        elif soh_analysis and soh_analysis.current_soh:
            soh_percentage = soh_analysis.current_soh

        card = CommunityCardResponse(
            shared_report_id=shared_report.id,
            user=UserInfo(
                username=user.username,
                phone_model=user.phone_model,
            ),
            device=DeviceInfo(
                manufacturer=device.manufacturer,
                model_name=device.model_name,
                powerbank_capacity_mah=device.powerbank_capacity_mah,
            ),
            session_stats=SessionStats(**session_stats),
            efficiency_stats=EfficiencyStats(
                soh_percentage=soh_percentage,
                efficiency_pct=efficiency_pct,
                mean_temperature_c=mean_temp,
            ),
            created_at=shared_report.created_at,
        )
        cards.append(card)

    return cards


def share_device_service(
    db: Session,
    request: CommunityShareRequest,
) -> CommunityShareResponse:
    """디바이스 공유 생성"""
    shared_report = create_shared_report(
        db,
        device_id=request.device_id,
        is_public=request.is_public,
    )
    return CommunityShareResponse(
        shared_report_id=shared_report.id,
        device_id=shared_report.device_id,
        is_public=shared_report.is_public,
        share_token=shared_report.share_token,
        created_at=shared_report.created_at,
    )


def update_share_status_service(
    db: Session,
    shared_report_id: int,
    is_public: bool,
) -> Optional[CommunityShareResponse]:
    """공유 상태 업데이트"""
    shared_report = update_shared_report(db, shared_report_id, is_public)
    if not shared_report:
        return None
    return CommunityShareResponse(
        shared_report_id=shared_report.id,
        device_id=shared_report.device_id,
        is_public=shared_report.is_public,
        share_token=shared_report.share_token,
        created_at=shared_report.created_at,
    )


def delete_share_service(db: Session, shared_report_id: int) -> bool:
    """공유 삭제"""
    return delete_shared_report(db, shared_report_id)


def get_filter_options_service(db: Session) -> dict:
    """필터링 옵션 조회 (폰 모델, 제조사, 용량)"""
    phone_models = get_distinct_phone_models(db)
    manufacturers = get_distinct_manufacturers(db)
    capacities = get_distinct_capacities(db)
    return {
        "phone_models": phone_models,
        "manufacturers": manufacturers,
        "capacities": capacities,
    }
