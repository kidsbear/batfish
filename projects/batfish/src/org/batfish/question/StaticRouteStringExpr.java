package org.batfish.question;

import org.batfish.main.BatfishException;
import org.batfish.representation.StaticRoute;

public enum StaticRouteStringExpr implements StringExpr {
   STATICROUTE_NEXT_HOP_INTERFACE;

   @Override
   public String evaluate(Environment environment) {
      StaticRoute staticRoute = environment.getStaticRoute();
      switch (this) {

      case STATICROUTE_NEXT_HOP_INTERFACE:
         return staticRoute.getNextHopInterface();

      default:
         throw new BatfishException("invalid static route string expr");

      }
   }

   @Override
   public String print(Environment environment) {
      return evaluate(environment);
   }

}