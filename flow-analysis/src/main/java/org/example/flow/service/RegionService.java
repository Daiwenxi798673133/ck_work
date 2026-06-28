package org.example.flow.service;

import org.example.flow.constant.SqlConst;
import org.example.flow.model.RegionVO;
import org.example.flow.repository.FlowRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegionService {

    private static final RowMapper<RegionVO> REGION_MAPPER = (rs, rowNum) -> new RegionVO(
            rs.getString("region_id"),
            rs.getString("region_name"),
            rs.getString("lac"),
            rs.getInt("cell_count"));

    private final FlowRepository flowRepository;

    public RegionService(FlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    public List<RegionVO> listRegions() {
        return flowRepository.query(SqlConst.REGIONS, REGION_MAPPER);
    }
}
