package org.batfish.datamodel.pojo;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.google.common.collect.Lists;
import com.google.common.testing.EqualsTester;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Analysis}. */
@RunWith(JUnit4.class)
public class AnalysisTest {

  @Test
  public void testConstructorWithName() {
    Analysis a = new Analysis("analysis");
    assertThat(a.getName(), equalTo("analysis"));
    assertNull(a.getQuestions());
  }

  @Test
  public void testBasicFunctions() {
    Analysis a =
        new Analysis(
            "analysis",
            Lists.newArrayList(Collections.singletonList(new AnalysisQuestion("question"))));
    assertThat(a.getName(), equalTo("analysis"));
    assertThat(
        a.getQuestions(),
        equalTo(Lists.newArrayList(Collections.singletonList(new AnalysisQuestion("question")))));
    a.setName("other-analysis");
    assertThat(a.getName(), equalTo("other-analysis"));
    a.setQuestions(
        Lists.newArrayList(Collections.singletonList(new AnalysisQuestion("other-question"))));
    assertThat(
        a.getQuestions(),
        equalTo(
            Lists.newArrayList(Collections.singletonList(new AnalysisQuestion("other-question")))));
  }

  @Test
  public void testToString() {
    Analysis a = new Analysis("foo", new ArrayList<>());
    assertThat(a.toString(), equalTo("Analysis{name=foo, questions=[]}"));
  }

  @Test
  public void testEquals() {
    Analysis a = new Analysis("foo", new ArrayList<>());
    Analysis aCopy = new Analysis("foo", new ArrayList<>());
    Analysis aWithQuestion =
        new Analysis(
            "foo", Lists.newArrayList(Collections.singletonList(new AnalysisQuestion("question"))));
    Analysis aOtherName = new Analysis("bar", new ArrayList<>());

    new EqualsTester()
        .addEqualityGroup(a, aCopy)
        .addEqualityGroup(aWithQuestion)
        .addEqualityGroup(aOtherName)
        .testEquals();
  }
}
