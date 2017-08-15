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

/** Tests for {@link Container}. */
@RunWith(JUnit4.class)
public class ContainerTest {
  @Test
  public void testFactoryWithName() {
    Container c = Container.of("container");
    assertThat(c.getName(), equalTo("container"));
    assertNull(c.getTestrigs());
    assertNull(c.getAnalyses());
  }

  @Test
  public void testFactoryWithAllValue() {
    Container c =
        Container.of(
            "container",
            Lists.newArrayList(Collections.singletonList(Testrig.of("testrig"))),
            Lists.newArrayList(Collections.singletonList(Analysis.of("analysis"))));
    assertThat(c.getName(), equalTo("container"));
    assertThat(
        c.getTestrigs(),
        equalTo(Lists.newArrayList(Collections.singletonList(Testrig.of("testrig")))));
    assertThat(
        c.getAnalyses(),
        equalTo(Lists.newArrayList(Collections.singletonList(Analysis.of("analysis")))));
  }

  @Test
  public void testToString() {
    Container c = Container.of("foo", new ArrayList<>(), new ArrayList<>());
    assertThat(c.toString(), equalTo("Container{name=foo, testrigs=[], analyses=[]}"));
  }

  @Test
  public void testEquals() {
    Container c = Container.of("foo", new ArrayList<>(), new ArrayList<>());
    Container cCopy = Container.of("foo", new ArrayList<>(), new ArrayList<>());
    Container cWithTestrig =
        Container.of(
            "foo",
            Lists.newArrayList(
                Collections.singletonList(
                    Testrig.of(
                        "testrig", new ArrayList<>(), new ArrayList<>(), new ArrayList<>()))),
            new ArrayList<>());
    Container cWithAnalyses =
        Container.of(
            "foo",
            new ArrayList<>(),
            Lists.newArrayList(
                Collections.singletonList(Analysis.of("analyses", new ArrayList<>()))));
    Container cOtherName = Container.of("bar", new ArrayList<>(), new ArrayList<>());

    new EqualsTester()
        .addEqualityGroup(c, cCopy)
        .addEqualityGroup(cWithTestrig)
        .addEqualityGroup(cWithAnalyses)
        .addEqualityGroup(cOtherName)
        .testEquals();
  }
}
