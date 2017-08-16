package org.batfish.datamodel.pojo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * The {@link Analysis Analysis} is an Object representation of the analysis for Batfish service.
 *
 * <p>Each {@link Analysis Analysis} contains a name and a list of {@link AnalysisQuestion
 * questions}.
 */
public class Analysis {
  private static final String PROP_NAME = "name";
  private static final String PROP_QUESTIONS = "questions";

  private String _name;
  private @Nullable List<AnalysisQuestion> _questions;

  public Analysis(@JsonProperty(PROP_NAME) String name) {
    this(name, null);
  }

  @JsonCreator
  public Analysis(
      @JsonProperty(PROP_NAME) String name,
      @Nullable @JsonProperty(PROP_QUESTIONS) List<AnalysisQuestion> questions) {
    this._name = name;
    this._questions = questions;
  }

  @JsonProperty(PROP_NAME)
  public String getName() {
    return _name;
  }

  @Nullable
  @JsonProperty(PROP_QUESTIONS)
  public List<AnalysisQuestion> getQuestions() {
    return _questions;
  }

  @JsonProperty(PROP_NAME)
  public void setName(String name) {
    _name = name;
  }

  @JsonProperty(PROP_QUESTIONS)
  public void setQuestions(List<AnalysisQuestion> questions) {
    _questions = questions;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(Analysis.class)
        .add(PROP_NAME, _name)
        .add(PROP_QUESTIONS, _questions)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Analysis)) {
      return false;
    }
    Analysis other = (Analysis) o;
    return Objects.equals(_name, other._name) && Objects.equals(_questions, other._questions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_name, _questions);
  }
}
