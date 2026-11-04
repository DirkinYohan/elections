package com.elections.modules.results.internal.repository;

import com.elections.modules.results.internal.model.AggregationLevel;
import com.elections.modules.results.internal.model.ElectoralAggregate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ResultsRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, ElectoralAggregate> inMemoryAggregates = new ConcurrentHashMap<>();

    public ResultsRepository(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public ElectoralAggregate findByLevelAndCode(AggregationLevel level, String code) {
        return inMemoryAggregates.computeIfAbsent(level.name() + ":" + code,
                k -> new ElectoralAggregate(level, code, 6400));
    }

    public void save(ElectoralAggregate aggregate) {
        inMemoryAggregates.put(aggregate.getLevel().name() + ":" + aggregate.getCode(), aggregate);
    }
}

