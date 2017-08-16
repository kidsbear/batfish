package org.batfish.datamodel.pojo;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.google.common.testing.EqualsTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AnalysisQuestion}. */
@RunWith(JUnit4.class)
public class AnalysisQuestionTest {
  // No tests for non-null question, because the creation of Question instance will cause a maven
  // dependency cycle

  @Test
  public void testConstructorWithName() {
    AnalysisQuestion question = new AnalysisQuestion("question");
    assertThat(question.getName(), equalTo("question"));
    assertNull(question.getQuestion());
  }

  @Test
  public void testBasicFunctions() {
    AnalysisQuestion question = new AnalysisQuestion("foo");
    assertThat(question.getName(), equalTo("foo"));
    assertNull(question.getQuestion());
    question.setName("bar");
    assertThat(question.getName(), equalTo("bar"));
  }

  @Test
  public void testToString() {
    AnalysisQuestion question = new AnalysisQuestion("foo");
    assertThat(question.toString(), equalTo("AnalysisQuestion{name=foo, question=null}"));
  }

  @Test
  public void testEquals() {
    AnalysisQuestion question = new AnalysisQuestion("foo");
    AnalysisQuestion questionCopy = new AnalysisQuestion("foo", null);
    AnalysisQuestion questionOtherName = new AnalysisQuestion("bar");

    new EqualsTester()
        .addEqualityGroup(question, questionCopy)
        .addEqualityGroup(questionOtherName)
        .testEquals();
  }
}
