package com.elections.modules.catalog;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Catálogo del departamento del caso: 1.842 puestos y 6.400 mesas repartidos en 25 municipios.
 * Es determinista, así que todas las instancias lo generan igual sin coordinarse.
 */
@Service
public class CatalogService {

    public static final String CORPORATION = "03";
    public static final List<String> CANDIDATE_KEYS = List.of("C1", "C2", "C3");
    public static final List<String> SPECIAL_KEYS = List.of("BLANCO", "NULO", "NO_MARCADO");
    public static final List<String> VOTE_KEYS;

    static {
        List<String> all = new ArrayList<>(CANDIDATE_KEYS);
        all.addAll(SPECIAL_KEYS);
        VOTE_KEYS = List.copyOf(all);
    }

    private static final int STATIONS = 1842;
    private static final int TABLES = 6400;
    private static final String[] MUNICIPALITIES = {
            "San Miguel", "Santa Rosa", "El Carmen", "La Esperanza", "Puerto Nuevo", "Villa del Río", "San Andrés",
            "Buenavista", "La Unión", "El Paraíso", "Monteverde", "San José", "Altamira", "Las Palmas", "Río Claro",
            "Santa Fe", "El Rosal", "Valle Alto", "La Florida", "San Pedro", "Piedras Blancas", "El Tambo",
            "Bellavista", "Los Andes", "Campo Alegre"};
    private static final String[] PLACES = {"Escuela", "Colegio", "Coliseo", "Institución Técnica", "Casa de la Cultura", "Universidad", "Centro Comunitario"};

    public record Table(int mesa, int puesto, int municipio) {}

    private final Map<String, Object> catalog;
    private final Map<Integer, Table> tables = new HashMap<>();
    private final Map<String, Integer> tablesTotal = new HashMap<>();

    public CatalogService() {
        List<Map<String, Object>> municipalities = new ArrayList<>();
        int mesa = 1;
        int puesto = 1;
        for (int m = 0; m < MUNICIPALITIES.length; m++) {
            int municipio = m + 1;
            int lastStation = (int) Math.round((double) STATIONS * (m + 1) / MUNICIPALITIES.length);
            List<Map<String, Object>> stations = new ArrayList<>();
            for (; puesto <= lastStation; puesto++) {
                int count = puesto <= TABLES - 3 * STATIONS ? 4 : 3; // 874 puestos con 4 mesas y 968 con 3 = 6.400
                List<Integer> stationTables = new ArrayList<>();
                for (int t = 0; t < count; t++, mesa++) {
                    stationTables.add(mesa);
                    tables.put(mesa, new Table(mesa, puesto, municipio));
                    tablesTotal.merge("department", 1, Integer::sum);
                    tablesTotal.merge("municipality:" + municipio, 1, Integer::sum);
                    tablesTotal.merge("station:" + puesto, 1, Integer::sum);
                }
                String name = PLACES[puesto % PLACES.length] + " " + puesto + " (" + MUNICIPALITIES[m] + ")";
                stations.add(Map.of("id", puesto, "name", name, "tables", stationTables));
            }
            municipalities.add(Map.of("id", municipio, "name", MUNICIPALITIES[m], "stations", stations));
        }
        catalog = Map.of(
                "department", "Departamento de demostración",
                "corporation", CORPORATION,
                "candidates", List.of(
                        Map.of("key", "C1", "name", "Laura Méndez", "party", "Movimiento Río"),
                        Map.of("key", "C2", "name", "Andrés Ospina", "party", "Partido Cordillera"),
                        Map.of("key", "C3", "name", "Carmen Ruiz", "party", "Coalición Horizonte")),
                "municipalities", municipalities);
    }

    public Map<String, Object> catalog() {
        return catalog;
    }

    public Optional<Table> table(int mesa) {
        return Optional.ofNullable(tables.get(mesa));
    }

    public Collection<Table> tables() {
        return tables.values();
    }

    public int tablesTotal(String topic) {
        return tablesTotal.getOrDefault(topic, 0);
    }

    public boolean isTopic(String topic) {
        return topic != null && tablesTotal.containsKey(topic);
    }

    public static String actaId(int puesto, int mesa) {
        return "E14-" + puesto + "-" + String.format("%04d", mesa) + "-" + CORPORATION;
    }
}
