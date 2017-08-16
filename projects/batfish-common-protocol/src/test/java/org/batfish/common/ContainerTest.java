package org.batfish.common;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.testing.EqualsTester;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeSet;
import org.batfish.datamodel.pojo.Analysis;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Container}. */
@RunWith(JUnit4.class)
public class ContainerTest {
  @Test
  public void testToString() {
    Container c = Container.of("foo", new TreeSet<>(), new ArrayList<>());
    assertThat(c.toString(), equalTo("Container{name=foo, testrigs=[], analyses=[]}"));
  }

  @Test
  public void testEquals() {
    Container c = Container.of("foo", new TreeSet<>(), new ArrayList<>());
    Container cCopy = Container.of("foo", new TreeSet<>(), new ArrayList<>());
    Container cWithTestrig =
        Container.of(
            "foo", Sets.newTreeSet(Collections.singletonList("testrig")), new ArrayList<>());
    Container cWithAnalyses =
        Container.of(
            "foo",
            new TreeSet<>(),
            Lists.newArrayList(Collections.singletonList(new Analysis("analysis"))));
    Container cOtherName = Container.of("bar", new TreeSet<>(), new ArrayList<>());

    new EqualsTester()
        .addEqualityGroup(c, cCopy)
        .addEqualityGroup(cWithTestrig)
        .addEqualityGroup(cWithAnalyses)
        .addEqualityGroup(cOtherName)
        .testEquals();
  }
}
