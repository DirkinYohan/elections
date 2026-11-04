package com.elections.infrastructure;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** JSON compartido (Jackson 3). Los mensajes de WebSocket, SSE y Redis se serializan con el mismo mapper. */
public final class Json {

    public static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private Json() {}

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static Map<String, Object> readMap(String json) {
        return MAPPER.readValue(json, MAP);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}
