package batfish.representation.juniper;

public class PolicyStatementSetMetricLine extends PolicyStatementSetLine {

   private int _metric;

   public PolicyStatementSetMetricLine(int metric) {
      _metric = metric;
   }

   @Override
   public SetType getSetType() {
      return SetType.METRIC;
   }

   public int getMetric() {
      return _metric;
   }
   
}
