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

/** Tests for {@link Testrig}. */
@RunWith(JUnit4.class)
public class TestrigTest {
  @Test
  public void testFactoryWithName() {
    Testrig t = Testrig.of("testrig");
    assertThat(t.getName(), equalTo("testrig"));
    assertNull(t.getConfigs());
    assertNull(t.getEnvironments());
    assertNull(t.getQuestions());
  }

  @Test
  public void testFactoryWithAllValue() {
    Testrig t =
        Testrig.of(
            "testrig",
            Lists.newArrayList(Collections.singletonList("configs")),
            Lists.newArrayList(Collections.singletonList("environments")),
            Lists.newArrayList(Collections.singletonList(TestrigQuestion.of("question"))));
    assertThat(t.getName(), equalTo("testrig"));
    assertThat(t.getConfigs(), equalTo(Lists.newArrayList(Collections.singletonList("configs"))));
    assertThat(
        t.getEnvironments(),
        equalTo(Lists.newArrayList(Collections.singletonList("environments"))));
    assertThat(
        t.getQuestions(),
        equalTo(Lists.newArrayList(Collections.singletonList(TestrigQuestion.of("question")))));
  }

  @Test
  public void testToString() {
    Testrig t = Testrig.of("foo", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    assertThat(
        t.toString(), equalTo("Testrig{name=foo, configs=[], environments=[], questions=[]}"));
  }

  @Test
  public void testEquals() {
    Testrig t = Testrig.of("foo", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    Testrig tCopy = Testrig.of("foo", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    Testrig tWithConfigs =
        Testrig.of("foo", Lists.newArrayList("configs"), new ArrayList<>(), new ArrayList<>());
    Testrig tWithenvs =
        Testrig.of("foo", new ArrayList<>(), Lists.newArrayList("environments"), new ArrayList<>());
    Testrig tWithQuestions =
        Testrig.of(
            "foo",
            new ArrayList<>(),
            new ArrayList<>(),
            Lists.newArrayList(TestrigQuestion.of("question")));
    Testrig tOtherName = Testrig.of("bar", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    new EqualsTester()
        .addEqualityGroup(t, tCopy)
        .addEqualityGroup(tWithConfigs)
        .addEqualityGroup(tWithenvs)
        .addEqualityGroup(tWithQuestions)
        .addEqualityGroup(tOtherName)
        .testEquals();
  }
}
