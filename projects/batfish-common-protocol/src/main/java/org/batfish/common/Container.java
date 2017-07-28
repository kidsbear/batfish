package org.batfish.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.glassfish.jersey.server.JSONP;

public final class Container {
  private static final String NAME_VAR = "name";
  private static final String TESTRIGS_VAR = "testrigs";
  private static final String ANALYSIS_VAR = "analysis";

  private String _name;
  private SortedSet<String> _testrigs;
  private Path _analysis;

  @JsonCreator
  public static Container makeContainer(
      @JsonProperty(NAME_VAR) String name, @JsonProperty(TESTRIGS_VAR) SortedSet<String> testrigs) {
    return new Container(name, testrigs);
  }

  private Container(String name, SortedSet<String> testrigs) {
    this._name = name;
    this._testrigs = testrigs;
  }

  @JsonProperty(NAME_VAR)
  public String getName() {
    return _name;
  }

  @JsonProperty(TESTRIGS_VAR)
  public SortedSet<String> getTestrigs() {
    return _testrigs;
  }

  @JsonProperty(ANALYSIS_VAR)
  public Path getAnalysis() {
    return _analysis;
  }

  @JsonProperty(NAME_VAR)
  public void setName(String name) {
    _name = name;
  }

  @JsonProperty(TESTRIGS_VAR)
  public void setTestrigs(SortedSet<String> testrigs) {
    _testrigs = testrigs;
  }

  @JsonProperty(ANALYSIS_VAR)
  public void setAnalysis(Path analysis) {
    _analysis = analysis;
  }
}
