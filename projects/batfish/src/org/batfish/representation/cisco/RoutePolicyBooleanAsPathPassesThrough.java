package org.batfish.representation.cisco;

import java.util.List;

import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.PassesThroughAsPath;
import org.batfish.datamodel.routing_policy.expr.SubRangeExpr;
import org.batfish.common.Warnings;

public class RoutePolicyBooleanAsPathPassesThrough extends RoutePolicyBoolean {

   /**
    *
    */
   private static final long serialVersionUID = 1L;

   private boolean _exact;

   private List<SubRangeExpr> _range;

   public RoutePolicyBooleanAsPathPassesThrough(List<SubRangeExpr> range,
         boolean exact) {
      _range = range;
      _exact = exact;
   }

   @Override
   public BooleanExpr toBooleanExpr(CiscoConfiguration cc, Configuration c,
         Warnings w) {
      return new PassesThroughAsPath(_range, _exact);
   }

}
