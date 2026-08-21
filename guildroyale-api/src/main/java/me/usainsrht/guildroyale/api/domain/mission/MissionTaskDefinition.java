package me.usainsrht.guildroyale.api.domain.mission;

import java.util.*;

/**
 * Immutable definition of a mission task as configured in the plugin config.
 */
public final class MissionTaskDefinition {

    private final String id;
    private final MissionTaskType type;
    private final String displayName;
    private final String iconMaterial;
    private final long target;
    private final Map<String, Object> properties;

    public MissionTaskDefinition(String id, MissionTaskType type, String displayName,
                                 String iconMaterial, long target, Map<String, Object> properties) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.displayName = displayName != null ? displayName : id;
        this.iconMaterial = iconMaterial != null ? iconMaterial : "PAPER";
        this.target = Math.max(1, target);
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    public String getId() { return id; }

    public MissionTaskType getType() { return type; }

    public String getDisplayName() { return displayName; }

    public String getIconMaterial() { return iconMaterial; }

    public long getTarget() { return target; }

    public Map<String, Object> getProperties() { return properties; }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getProperty(String key, Class<T> type) {
        Object val = properties.get(key);
        if (val != null && type.isInstance(val)) {
            return Optional.of((T) val);
        }
        return Optional.empty();
    }

    public String getStringProperty(String key, String def) {
        Object val = properties.get(key);
        return val != null ? val.toString() : def;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringListProperty(String key) {
        Object val = properties.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        } else if (val instanceof String s) {
            return List.of(s);
        }
        return List.of();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MissionTaskDefinition that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "MissionTaskDefinition{id='" + id + "', type=" + type + ", target=" + target + '}';
    }
}
