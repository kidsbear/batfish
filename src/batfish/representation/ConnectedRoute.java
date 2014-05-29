package batfish.representation;

import batfish.util.Util;

public class ConnectedRoute extends Route {

   public ConnectedRoute(Ip prefix, int prefixLength, Ip nextHopIp) {
      super(prefix, prefixLength, nextHopIp);
   }

   @Override
   public int getAdministrativeCost() {
      return 0;
   }

   @Override
   public RouteType getRouteType() {
      return RouteType.CONNECTED;
   }

   @Override
   public boolean equals(Object o) {
      ConnectedRoute rhs = (ConnectedRoute) o;
      return _prefix.equals(rhs._prefix) && _prefixLength == rhs._prefixLength
            && _nextHopIp.equals(rhs._nextHopIp);
   }
   
   @Override
   public String getIFString(int indentLevel) {
	   return Util.getIndentString(indentLevel) +  "ConnectedRoute " + getRouteString();
   }
}
