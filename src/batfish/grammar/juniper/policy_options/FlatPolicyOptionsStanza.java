package batfish.grammar.juniper.policy_options;

import java.util.ArrayList;
import java.util.List;

import batfish.grammar.juniper.JStanza;
import batfish.grammar.juniper.JStanzaType;
import batfish.representation.juniper.ASPathAccessList;
import batfish.representation.juniper.ExpandedCommunityList;
import batfish.representation.juniper.ExpandedCommunityListLine;
import batfish.representation.juniper.RouteFilter;
import batfish.representation.juniper.PolicyStatement;

public class FlatPolicyOptionsStanza extends JStanza {

   private List<PolicyStatement> _maps;
   private List<RouteFilter> _filters;
   private List<ExpandedCommunityList> _communities;
   private List<ASPathAccessList> _asPathLists;
   
   private boolean _isFrom;
   private FromType _fType;
   private ThenType _tType;
   private POType _type1;

   public FlatPolicyOptionsStanza() {
      _maps = new ArrayList<PolicyStatement>();
      _filters = new ArrayList<RouteFilter>();
      _communities = new ArrayList<ExpandedCommunityList>();
      _asPathLists = new ArrayList<ASPathAccessList>();
   }

   public void processStanza(POStanza ps) {
      _type1 = ps.getType();
      switch (ps.getType()) {
      case NULL:
         break;

      case POLICY_STATEMENT:         
         FlatPolicyStatementPOStanza psps = (FlatPolicyStatementPOStanza) ps;
         _isFrom = psps.isFrom();
         _fType = psps.getFType();
         _tType = psps.getTType();
         if (!psps.isIPv6()) {
            _maps.add(psps.getMap());
            _filters.addAll(psps.getRouteFilters());
         }
         break;

      case PREFIX_LIST:
         FlatPrefixListPOStanza plps = (FlatPrefixListPOStanza) ps;
         if (!plps.isIPv6()) {
            _filters.add(plps.getRouteFilter());
         }
         break;

      case COMMUNITY:
         CommunityPOStanza cps = (CommunityPOStanza) ps;
         ExpandedCommunityList ecl = new ExpandedCommunityList(cps.getName());
         for (ExpandedCommunityListLine ecll : cps.getLines()) {
            ecl.addLine(ecll);
         }
         _communities.add(ecl);
         break;

      case AS_PATH:
         ASPathPOStanza aps = (ASPathPOStanza)ps;
         ASPathAccessList aal = new ASPathAccessList(aps.getName(), aps.getLines());
         _asPathLists.add(aal);
         break;
         
      default:
         System.out.println("bad policy options stanza type");
         break;
      }
   }

   public List<PolicyStatement> getMaps() {
      return _maps;
   }

   public List<RouteFilter> getFilters() {
      return _filters;
   }

   public List<ExpandedCommunityList> getCommunities() {
      return _communities;
   }
   
   public List<ASPathAccessList> getAsPathLists(){
      return _asPathLists;
   }
   
   public boolean isFrom(){
      return _isFrom;
   }
   
   public FromType getFType(){
      return _fType;      
   }
   
   public ThenType getTType(){
      return _tType;
   }
   
   public POType getType1(){
      return _type1;
   }

   @Override
   public JStanzaType getType() {
      return JStanzaType.POLICY_OPTIONS;
   }

}
