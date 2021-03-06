package org.batfish.datamodel;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.batfish.common.BatfishException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class Ip implements Comparable<Ip>, Serializable {

   private static Map<Ip, BitSet> _addressBitsCache = new ConcurrentHashMap<>();

   public static final Ip AUTO = new Ip(-1l);

   public static final Ip MAX = new Ip(0xFFFFFFFFl);

   private static final int NUM_BYTES = 4;

   private static final long serialVersionUID = 1L;

   public static final Ip ZERO = new Ip(0l);

   private static long ipStrToLong(String addr) {
      String[] addrArray = addr.split("\\.");
      if (addrArray.length != 4) {
         if (addr.startsWith("INVALID_IP") || addr.startsWith("AUTO/NONE")) {
            String[] tail = addr.split("\\(");
            if (tail.length == 2) {
               String[] longStrParts = tail[1].split("l");
               if (longStrParts.length == 2) {
                  String longStr = longStrParts[0];
                  return Long.parseLong(longStr);
               }
            }
         }
         throw new BatfishException("Invalid ip string: \"" + addr + "\"");
      }
      long num = 0;
      for (int i = 0; i < addrArray.length; i++) {
         int power = 3 - i;
         String segmentStr = addrArray[i];
         try {
            int segment = Integer.parseInt(segmentStr);
            num += ((segment % 256 * Math.pow(256, power)));
         }
         catch (NumberFormatException e) {
            throw new BatfishException("Invalid ip segment: \"" + segmentStr
                  + "\" in ip string: \"" + addr + "\"", e);
         }
      }
      return num;
   }

   private static long numSubnetBitsToSubnetLong(int numBits) {
      long val = 0;
      for (int i = 31; i > 31 - numBits; i--) {
         val |= ((long) 1 << i);
      }
      return val;
   }

   public static Ip numSubnetBitsToSubnetMask(int numBits) {
      long mask = numSubnetBitsToSubnetLong(numBits);
      return new Ip(mask);
   }

   private final long _ip;

   public Ip(long ipAsLong) {
      _ip = ipAsLong;
   }

   @JsonCreator
   public Ip(String ipAsString) {
      _ip = ipStrToLong(ipAsString);
   }

   public long asLong() {
      return _ip;
   }

   @Override
   public int compareTo(Ip rhs) {
      return Long.compare(_ip, rhs._ip);
   }

   @Override
   public boolean equals(Object o) {
      Ip rhs = (Ip) o;
      return _ip == rhs._ip;
   }

   public BitSet getAddressBits() {
      BitSet bits = _addressBitsCache.get(this);
      if (bits == null) {
         int addressAsInt = (int) (_ip);
         ByteBuffer b = ByteBuffer.allocate(NUM_BYTES);
         b.order(ByteOrder.LITTLE_ENDIAN); // optional, the initial order of a
                                           // byte
                                           // buffer is always BIG_ENDIAN.
         b.putInt(addressAsInt);
         BitSet bitsWithHighestMostSignificant = BitSet.valueOf(b.array());
         bits = new BitSet(Prefix.MAX_PREFIX_LENGTH);
         for (int i = Prefix.MAX_PREFIX_LENGTH - 1, j = 0; i >= 0; i--, j++) {
            bits.set(j, bitsWithHighestMostSignificant.get(i));
         }
         _addressBitsCache.put(this, bits);
      }
      return bits;
   }

   public Ip getClassMask() {
      long firstOctet = _ip >> 24;
      if (firstOctet <= 126) {
         return new Ip(0xFF000000l);
      }
      else if (firstOctet >= 128 && firstOctet <= 191) {
         return new Ip(0XFFFF0000l);
      }
      else if (firstOctet >= 192 && firstOctet <= 223) {
         return new Ip(0xFFFFFF00l);
      }
      else {
         throw new BatfishException("Cannot compute classmask");
      }
   }

   public Ip getNetworkAddress(int subnetBits) {
      long mask = numSubnetBitsToSubnetLong(subnetBits);
      return new Ip(_ip & mask);
   }

   public Ip getNetworkAddress(Ip mask) {
      return new Ip(_ip & mask.asLong());
   }

   public Ip getSubnetEnd(Ip mask) {
      return new Ip(_ip | mask.inverted().asLong());
   }

   public Ip getWildcardEndIp(Ip wildcard) {
      return new Ip(_ip | wildcard.asLong());
   }

   @Override
   public int hashCode() {
      return Long.hashCode(_ip);
   }

   public Ip inverted() {
      long invertedLong = (~_ip) & 0xFFFFFFFFl;
      return new Ip(invertedLong);
   }

   public String networkString(int prefixLength) {
      return toString() + "/" + prefixLength;
   }

   public String networkString(Ip mask) {
      return toString() + "/" + mask.numSubnetBits();
   }

   public int numSubnetBits() {
      int numTrailingZeros = Long.numberOfTrailingZeros(_ip);
      if (numTrailingZeros > Prefix.MAX_PREFIX_LENGTH) {
         return 0;
      }
      else {
         return Prefix.MAX_PREFIX_LENGTH - numTrailingZeros;
      }
   }

   @Override
   @JsonValue
   public String toString() {
      if (!valid()) {
         if (_ip == -1l) {
            return "AUTO/NONE(-1l)";
         }
         else {
            return "INVALID_IP(" + _ip + "l)";
         }
      }
      else {
         return ((_ip >> 24) & 0xFF) + "." + ((_ip >> 16) & 0xFF) + "."
               + ((_ip >> 8) & 0xFF) + "." + (_ip & 0xFF);
      }
   }

   public boolean valid() {
      return 0l <= _ip && _ip <= 0xFFFFFFFFl;
   }

}
