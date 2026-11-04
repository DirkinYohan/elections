package com.elections.modules.ballots;

import com.elections.infrastructure.Json;
import com.elections.infrastructure.chaos.ChaosService;
import com.elections.infrastructure.database.PersistenceService;
import com.elections.infrastructure.metrics.MetricsService;
import com.elections.infrastructure.redis.LuaScripts;
import com.elections.modules.catalog.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Recepción de actas (RF-1, RF-2, RF-3).
 *
 * Nunca se responde éxito sin haber persistido en Redis: el script submit.lua registra el acta, su
 * clave de idempotencia y el recibo en la misma operación atómica. Si el servidor cae justo después
 * de registrar y antes de responder, el jurado no recibe nada, su app reenvía con el mismo "idem" y
 * recibe DUPLICATE con el recibo original. Si la clave idem ya expiró (24 h), la segunda línea de
 * defensa es la clave permanente acta#versión en el hash "submissions".
 */
@Service
public class BallotService {

    private static final Logger log = LoggerFactory.getLogger(BallotService.class);

    public record Outcome(int httpStatus, Map<String, Object> body) {}

    private final LuaScripts lua;
    private final CatalogService catalog;
    private final ChaosService chaos;
    private final MetricsService metrics;
    private final PersistenceService persistence;
    private final long idemTtlSeconds;

    public BallotService(LuaScripts lua, CatalogService catalog, ChaosService chaos, MetricsService metrics,
                         PersistenceService persistence, @Value("${app.ballots.idem-ttl-seconds:86400}") long idemTtlSeconds) {
        this.lua = lua;
        this.catalog = catalog;
        this.chaos = chaos;
        this.metrics = metrics;
        this.persistence = persistence;
        this.idemTtlSeconds = idemTtlSeconds;
    }

    public Outcome submit(Map<String, Object> sheet) {
        String acta = sheet == null ? "" : String.valueOf(sheet.getOrDefault("acta", ""));
        int version = sheet != null && sheet.get("version") instanceof Number n ? n.intValue() : 0;
        if (chaos.redisDown()) return unavailable(acta, version);

        String error = validate(sheet);
        if (error != null) {
            return new Outcome(422, response("REJECTED", acta, version, error));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> votos = (Map<String, Object>) sheet.get("votos");
        int sum = votos.values().stream().mapToInt(v -> ((Number) v).intValue()).sum();
        int sufragantes = ((Number) sheet.get("sufragantes")).intValue();
        boolean doubtful = sum != sufragantes;
        long now = System.currentTimeMillis();
        String receipt = "R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String votosJson = Json.write(votos);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ballotId", acta);
        item.put("version", version);
        item.put("municipio", sheet.get("municipio"));
        item.put("puesto", sheet.get("puesto"));
        item.put("mesa", sheet.get("mesa"));
        item.put("jurado", sheet.get("jurado"));
        item.put("priority", Math.min(5, 1 + Math.abs(sum - sufragantes)));
        item.put("reason", "La suma de votos (" + sum + ") no coincide con sufragantes (" + sufragantes + ")");
        item.put("votos", votos);
        item.put("sufragantes", sufragantes);
        item.put("createdAt", now);

        List<String> result;
        try {
            result = lua.submit(acta, String.valueOf(version), String.valueOf(sheet.get("idem")), doubtful ? "1" : "0",
                    votosJson, String.valueOf(sheet.get("mesa")), String.valueOf(sheet.get("puesto")), String.valueOf(sheet.get("municipio")),
                    receipt, String.valueOf(now), Json.write(item), String.valueOf(item.get("priority")), String.valueOf(idemTtlSeconds),
                    item.get("reason") + ". Pasa a verificación.", Json.write(Map.of("jurado", String.valueOf(sheet.get("jurado")))));
        } catch (DataAccessException e) {
            log.warn("Redis no responde al registrar {}: {}", acta, e.getMessage());
            return unavailable(acta, version);
        }

        String status = result.get(0);
        Map<String, Object> body = new LinkedHashMap<>(Json.readMap(result.get(1)));
        if ("DUPLICATE".equals(status)) {
            body.put("message", "Ya estaba registrada (" + body.get("status") + ")");
            body.put("status", "DUPLICATE");
            return new Outcome(200, body);
        }

        metrics.recordBallot();
        persistence.saveSubmission(sheet, status, receipt, votosJson);
        persistence.audit("BALLOT_" + status, acta, String.valueOf(sheet.get("jurado")), "v" + version + " · " + body.get("message"));
        int http = switch (status) {
            case "REGISTERED" -> 201;
            case "DOUBTFUL" -> 202;
            default -> 200;
        };
        return new Outcome(http, body);
    }

    private Outcome unavailable(String acta, int version) {
        return new Outcome(503, response("UNAVAILABLE", acta, version,
                "Sistema degradado: el acta NO quedó registrada. Se reintentará sola."));
    }

    private static Map<String, Object> response(String status, String acta, int version, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("ballotId", acta);
        m.put("version", version);
        m.put("message", message);
        return m;
    }

    /** Validación pura del mensaje de la sección 3.1 contra el catálogo. */
    String validate(Map<String, Object> s) {
        if (s == null) return "Cuerpo vacío o no es JSON";
        if (!(s.get("acta") instanceof String acta) || !acta.matches("^E14-\\d+-\\d+-\\d+$"))
            return "\"acta\" debe tener la forma E14-puesto-mesa-corporación";
        if (!(s.get("idem") instanceof String idem) || idem.isBlank() || idem.length() > 128) return "Falta \"idem\"";
        for (String f : List.of("mesa", "puesto", "municipio", "version", "sufragantes")) {
            if (!(s.get(f) instanceof Number n) || n.doubleValue() != Math.floor(n.doubleValue()) || n.longValue() < 0)
                return "\"" + f + "\" debe ser un entero no negativo";
        }
        if (((Number) s.get("version")).intValue() < 1) return "\"version\" empieza en 1";
        if (!(s.get("votos") instanceof Map<?, ?> votos) || votos.isEmpty()) return "Faltan los votos";
        for (var e : votos.entrySet()) {
            if (!CatalogService.VOTE_KEYS.contains(String.valueOf(e.getKey()))) return "Clave de voto desconocida: " + e.getKey();
            if (!(e.getValue() instanceof Number n) || n.doubleValue() != Math.floor(n.doubleValue()) || n.longValue() < 0 || n.longValue() > 100_000)
                return "Votos inválidos para " + e.getKey();
        }
        int mesa = ((Number) s.get("mesa")).intValue();
        int puesto = ((Number) s.get("puesto")).intValue();
        int municipio = ((Number) s.get("municipio")).intValue();
        var table = catalog.table(mesa);
        if (table.isEmpty() || table.get().puesto() != puesto || table.get().municipio() != municipio)
            return "La mesa " + mesa + " no pertenece al puesto " + puesto + " del municipio " + municipio;
        if (!CatalogService.actaId(puesto, mesa).equals(acta))
            return "El acta debería ser " + CatalogService.actaId(puesto, mesa);
        return null;
    }
}
