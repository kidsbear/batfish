package batfish.util;

public abstract class NamedStructure implements Comparable<NamedStructure> {
   protected String _name;

   public NamedStructure(String name) {
      _name = name;
   }
   
   @Override
   public int compareTo(NamedStructure rhs) {
      return _name.compareTo(rhs._name);
   }

   @Override
   public boolean equals(Object o) {
      NamedStructure rhs = (NamedStructure) o;
      return _name.equals(rhs._name);
   }

   @Override
   public int hashCode() {
      return _name.hashCode();
   }

   public String getName() {
      return _name;
   }

   @Override
   public String toString() {
      return getClass().getSimpleName() + "<" + _name + ">";
   }
   
}
