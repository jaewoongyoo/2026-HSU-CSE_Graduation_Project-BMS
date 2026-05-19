from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.community import (
    CommunityCardResponse,
    CommunityDeleteResponse,
    CommunityFilterRequest,
    CommunityShareRequest,
    CommunityShareResponse,
    CommunityShareUpdateRequest,
)
from app.services.community_service import (
    delete_share_service,
    get_community_feed,
    get_community_feed_by_filter,
    get_filter_options_service,
    share_device_service,
    update_share_status_service,
)

router = APIRouter(prefix="/api/v1/community", tags=["community"])


@router.get("", response_model=list[CommunityCardResponse])
def get_community(
    limit: int = 50,
    offset: int = 0,
    db: Session = Depends(get_db),
):
    """
    전체 커뮤니티 피드 조회
    
    - **limit**: 조회 수 제한 (기본값: 50)
    - **offset**: 오프셋 (기본값: 0, 페이지네이션용)
    """
    return get_community_feed(db, limit=limit, offset=offset)


@router.post("/filter", response_model=list[CommunityCardResponse])
def get_community_filtered(
    request: CommunityFilterRequest,
    db: Session = Depends(get_db),
):
    """
    필터링된 커뮤니티 피드 조회
    
    - **phone_models**: 폰 모델 필터 (OR 조건)
    - **manufacturers**: 배터리 제조사 필터 (OR 조건)
    - **soh_min/max**: SOH 범위 필터 (0~100)
    """
    return get_community_feed_by_filter(db, request)


@router.get("/filter-options", response_model=dict)
def get_filter_options(
    db: Session = Depends(get_db),
):
    """
    필터링 옵션 조회 (사용 가능한 폰 모델, 배터리 제조사 목록)
    """
    return get_filter_options_service(db)


@router.post("/{device_id}/share", response_model=CommunityShareResponse)
def share_device(
    device_id: int,
    request: CommunityShareRequest,
    db: Session = Depends(get_db),
):
    """
    디바이스를 커뮤니티에 공유
    
    - **device_id**: 공유할 디바이스 ID
    - **is_public**: 공개 여부 (기본값: true)
    """
    try:
        return share_device_service(db, request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Failed to share device")


@router.patch("/{shared_report_id}", response_model=CommunityShareResponse)
def update_share_status(
    shared_report_id: int,
    request: CommunityShareUpdateRequest,
    db: Session = Depends(get_db),
):
    """
    공유 상태 업데이트
    
    - **shared_report_id**: 공유 보고서 ID
    - **is_public**: 공개 여부
    """
    result = update_share_status_service(db, shared_report_id, request.is_public)
    if not result:
        raise HTTPException(status_code=404, detail="Shared report not found")
    return result


@router.delete("/{shared_report_id}", response_model=CommunityDeleteResponse)
def delete_share(
    shared_report_id: int,
    db: Session = Depends(get_db),
):
    """
    공유 삭제
    
    - **shared_report_id**: 공유 보고서 ID
    """
    success = delete_share_service(db, shared_report_id)
    if not success:
        raise HTTPException(status_code=404, detail="Shared report not found")
    return CommunityDeleteResponse(
        success=True,
        message="Shared report deleted successfully",
    )
