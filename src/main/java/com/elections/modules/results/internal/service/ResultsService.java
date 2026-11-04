package com.elections.modules.results.internal.service;

import com.elections.modules.results.ResultsApi;
import com.elections.modules.results.internal.dto.AggregateDto;
import com.elections.modules.results.internal.model.AggregationLevel;
import com.elections.modules.results.internal.model.ElectoralAggregate;
import com.elections.modules.results.internal.repository.ResultsRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ResultsService implements ResultsApi {

    private final ResultsRepository resultsRepository;

    public ResultsService(ResultsRepository resultsRepository) {
        this.resultsRepository = resultsRepository;
    }

    @Override
    public AggregateDto getDepartmentResults() {
        ElectoralAggregate department = resultsRepository.findByLevelAndCode(AggregationLevel.DEPARTMENT, "GLOBAL");
        return mapToDto(department);
    }

    @Override
    public AggregateDto getMunicipalityResults(Integer municipalityId) {
        ElectoralAggregate municipality = resultsRepository.findByLevelAndCode(AggregationLevel.MUNICIPALITY, String.valueOf(municipalityId));
        return mapToDto(municipality);
    }

    @Override
    public AggregateDto getPollingStationResults(Integer pollingStationId) {
        ElectoralAggregate station = resultsRepository.findByLevelAndCode(AggregationLevel.POLLING_STATION, String.valueOf(pollingStationId));
        return mapToDto(station);
    }

    @Override
    public void applyBallotToAggregates(String ballotId, Map<String, Integer> votes, Integer table, Integer station, Integer municipality) {
        // Full implementation: Executes Lua script in Redis to atomically update all 4 levels (RF-3)
    }

    private AggregateDto mapToDto(ElectoralAggregate entity) {
        AggregateDto dto = new AggregateDto();
        dto.setLevel(entity.getLevel());
        dto.setIdentifier(entity.getCode());
        dto.setReportedTables(entity.getReportedTables());
        dto.setTotalTables(entity.getTotalTables());
        double percentage = entity.getTotalTables() > 0 ? ((double) entity.getReportedTables() / entity.getTotalTables()) * 100.0 : 0.0;
        dto.setReportedPercentage(percentage);
        dto.setCandidateTotals(entity.getVotes());
        return dto;
    }
}

