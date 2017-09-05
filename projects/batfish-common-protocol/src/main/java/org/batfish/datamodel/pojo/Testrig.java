package org.batfish.datamodel.pojo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Date;
import java.util.Objects;

/**
 * The {@link Testrig Testrig} is an Object representation of the testrig for BatFish service.
 *
 * <p>Each {@link Testrig Testrig} contains its name, and a summary of the testrig {@link #_name}.
 */
public class Testrig {
  private static final String PROP_NAME = "name";
  private static final String PROP_CREATED_AT = "createdAt";
  private static final String PROP_ENVIRONMENTS_COUNT = "environmentsCount";
  private static final String PROP_CONFIGURATIONS_COUNT = "configurationsCount";

  private final String _name;
  private final Date _createdAt;
  private final int _environmentsCount;
  private final int _configurationsCount;

  @JsonCreator
  public Testrig(
      @JsonProperty(PROP_NAME) String name,
      @JsonProperty(PROP_CREATED_AT) Date createdAt,
      @JsonProperty(PROP_ENVIRONMENTS_COUNT) int environmentsCount,
      @JsonProperty(PROP_CONFIGURATIONS_COUNT) int configurationsCount) {
    this._name = name;
    this._createdAt = createdAt;
    this._environmentsCount = environmentsCount;
    this._configurationsCount = configurationsCount;
  }

  @JsonProperty(PROP_NAME)
  public String getName() {
    return _name;
  }

  @JsonProperty(PROP_CREATED_AT)
  public Date getCreatedAt() {
    return _createdAt;
  }

  @JsonProperty(PROP_ENVIRONMENTS_COUNT)
  public int getEnvironmentsCount() {
    return _environmentsCount;
  }

  @JsonProperty(PROP_CONFIGURATIONS_COUNT)
  public int getConfigurationsCount() {
    return _configurationsCount;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(Testrig.class)
        .add(PROP_NAME, _name)
        .add(PROP_CREATED_AT, _createdAt)
        .add(PROP_ENVIRONMENTS_COUNT, _environmentsCount)
        .add(PROP_CONFIGURATIONS_COUNT, _configurationsCount)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Testrig)) {
      return false;
    }
    Testrig other = (Testrig) o;
    return Objects.equals(_name, other._name)
        && Objects.equals(_createdAt, other._createdAt)
        && Objects.equals(_environmentsCount, other._environmentsCount)
        && Objects.equals(_configurationsCount, other._configurationsCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_name, _createdAt, _environmentsCount, _configurationsCount);
  }
}
