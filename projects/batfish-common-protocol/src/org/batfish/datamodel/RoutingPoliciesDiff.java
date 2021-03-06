package org.batfish.datamodel;

import java.util.NavigableMap;

import org.batfish.datamodel.routing_policy.RoutingPolicy;
import com.fasterxml.jackson.annotation.JsonCreator;

public class RoutingPoliciesDiff extends ConfigDiffElement {

   @JsonCreator()
   public RoutingPoliciesDiff() {
   }

   public RoutingPoliciesDiff(NavigableMap<String, RoutingPolicy> before,
         NavigableMap<String, RoutingPolicy> after) {
      super(before, after, false);
   }

   @Override
   protected boolean skip(String name) {
      return name.startsWith("~") && name.endsWith("~");
   }

}
