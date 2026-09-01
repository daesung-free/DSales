package com.daesung.sales.material.service;

import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.material.dto.MaterialRequest;
import com.daesung.sales.material.dto.MaterialResponse;
import com.daesung.sales.material.entity.Material;
import com.daesung.sales.material.repository.MaterialRepository;
import com.daesung.sales.product.entity.MaterialType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자재 마스터 CRUD(33p 세트구성 탭). 근거: 발주처 구조보완요청안(2026-08-31) —
 * 자재를 BOM에 매번 입력하지 않고 별도 목록에서 관리한다.
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final CurrentAuditor currentAuditor;

    @Transactional(readOnly = true)
    public List<MaterialResponse> search(MaterialType type, Boolean useYn, String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return materialRepository.search(type, useYn, kw).stream()
                .map(MaterialResponse::from)
                .toList();
    }

    @Transactional
    public MaterialResponse create(MaterialRequest req) {
        if (materialRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 있는 자재코드입니다: " + req.code());
        }
        Material m = Material.create(req.code(), req.name(), req.materialType(), req.memo());
        if (Boolean.FALSE.equals(req.useYn())) {
            m.update(null, null, false, req.memo());
        }
        return MaterialResponse.from(materialRepository.save(m));
    }

    @Transactional
    public MaterialResponse update(Long id, MaterialRequest req) {
        Material m = getOrThrow(id);
        m.update(req.name(), req.materialType(), req.useYn(), req.memo());
        return MaterialResponse.from(m);
    }

    /**
     * 논리삭제. ★<b>BOM에 매칭돼 있으면 막는다</b> —
     * 지우면 그 세트의 소요수량이 조용히 사라져 물류 작업비가 줄어든다.
     * 쓰지 않으려면 {@code useYn=false}로 두면 목록에서만 빠지고 기존 매칭은 유지된다.
     */
    @Transactional
    public void delete(Long id, long matchedCount) {
        if (matchedCount > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "세트 구성에 매칭된 자재는 삭제할 수 없습니다(" + matchedCount + "건). "
                            + "매칭을 먼저 해제하거나 사용여부를 N으로 두세요.");
        }
        getOrThrow(id).markDeleted(currentAuditor.username());
    }

    @Transactional(readOnly = true)
    public Material getOrThrow(Long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "자재가 없습니다. id=" + id));
    }
}
