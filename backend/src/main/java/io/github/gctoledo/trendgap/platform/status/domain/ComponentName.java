package io.github.gctoledo.trendgap.platform.status.domain;

/** Components reported by the status endpoint, in their fixed display order. */
public enum ComponentName {
  BACKEND("backend"),
  DATABASE("database"),
  ML_SERVICE("ml-service");

  private final String value;

  ComponentName(String value) {
    this.value = value;
  }

  /** Stable name used in JSON. */
  public String value() {
    return value;
  }
}
