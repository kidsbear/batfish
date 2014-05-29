package batfish.grammar.cisco;

import batfish.representation.cisco.CiscoVendorConfiguration;

public class CiscoConfiguration {

   private CiscoVendorConfiguration _configuration;

   public CiscoConfiguration() {
      _configuration = new CiscoVendorConfiguration();
   }

   public CiscoVendorConfiguration getConfiguration() {
      return _configuration;
   }

   public void processStanza(Stanza stanza) {
      if (stanza == null) {
         return;
      }
      stanza.process(_configuration);
   }
}
