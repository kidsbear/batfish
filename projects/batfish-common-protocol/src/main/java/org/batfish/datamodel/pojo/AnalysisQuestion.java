package org.batfish.datamodel.pojo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import javax.annotation.Nullable;
import org.batfish.datamodel.questions.Question;

/**
 * The {@link AnalysisQuestion AnalysisQuestion} is an Object representation of the question used
 * for Batfish service.
 *
 * <p>{@link AnalysisQuestion AnalysisQuestion} contains a list of {@link Question questions} that
 * need to be analyzed, along with the name of the question.
 */
public class AnalysisQuestion {

  private static final String PROP_NAME = "name";
  private static final String PROP_QUESTION = "question";

  private String _name;
  private @Nullable Question _question;

  public AnalysisQuestion(@JsonProperty(PROP_NAME) String name) {
    this(name, null);
  }

  @JsonCreator
  public AnalysisQuestion(
      @JsonProperty(PROP_NAME) String name,
      @Nullable @JsonProperty(PROP_QUESTION) Question question) {
    this._name = name;
    this._question = question;
  }

  @JsonProperty(PROP_NAME)
  public String getName() {
    return _name;
  }

  @Nullable
  @JsonProperty(PROP_QUESTION)
  public Question getQuestion() {
    return _question;
  }

  @JsonProperty(PROP_NAME)
  public void setName(String name) {
    _name = name;
  }

  @JsonProperty(PROP_QUESTION)
  public void setQuestion(Question question) {
    _question = question;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(AnalysisQuestion.class)
        .add(PROP_NAME, _name)
        .add(PROP_QUESTION, _question)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AnalysisQuestion)) {
      return false;
    }
    AnalysisQuestion other = (AnalysisQuestion) o;
    return Objects.equals(_name, other._name) && Objects.equals(_question, other._question);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_name, _question);
  }
}
