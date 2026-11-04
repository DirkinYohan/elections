package com.elections.modules.results;

import com.elections.modules.results.internal.dto.AggregateDto;

import java.util.Map;

/**
 * Public API for the Electoral Results Module.
 * Exposes hierarchical aggregates (department, municipality, polling station, polling table).
 */
public interface ResultsApi {

    AggregateDto getDepartmentResults();

    AggregateDto getMunicipalityResults(Integer municipalityId);

    AggregateDto getPollingStationResults(Integer pollingStationId);

    void applyBallotToAggregates(String ballotId, Map<String, Integer> votes, Integer table, Integer station, Integer municipality);
}

