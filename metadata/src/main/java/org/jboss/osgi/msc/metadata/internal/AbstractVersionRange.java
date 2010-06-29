/*
* JBoss, Home of Professional Open Source
* Copyright 2006, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.osgi.msc.metadata.internal;

import java.util.StringTokenizer;

import org.jboss.osgi.msc.metadata.VersionRange;
import org.osgi.framework.Version;

/**
 * Represents an OSGi version range:
 * version-range ::= interval | atleast
 * interval ::= ( '[' | '(' ) floor ',' ceiling ( ']' | ')' )
 * atleast ::= version
 * floor ::= version
 * ceiling ::= version
 *
 * [TODO] do we really need this extra class or just use our version range?
 * @author Scott.Stark@jboss.org
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 * @author adrian@jboss.org
 */
public class AbstractVersionRange implements VersionRange
{
   /**
    * Get the version range from a string
    * 
    * @param rangeSpec the range spec
    * @return the version range
    */
   public static AbstractVersionRange valueOf(String rangeSpec)
   {
      return parseRangeSpec(rangeSpec);
   }

   /**
    * Parse a range spec
    * 
    * @param rangeSpec
    * @return the version range
    */
   public static AbstractVersionRange parseRangeSpec(String rangeSpec)
   {
      if (rangeSpec == null)
         throw new IllegalArgumentException("Null rangeSpec");
      
      // Handle version strings with quotes 
      if (rangeSpec.startsWith("\"") && rangeSpec.endsWith("\""))
         rangeSpec = rangeSpec.substring(1, rangeSpec.length() - 1);
      
      Version floor = null;
      Version ceiling = null;
      StringTokenizer st = new StringTokenizer(rangeSpec, ",[]()", true);
      Boolean floorIsGreaterThan = null;
      Boolean ceilingIsLessThan = null;
      boolean mid = false;
      while (st.hasMoreTokens())
      {
         String token = st.nextToken();
         if (token.equals("["))
            floorIsGreaterThan = false;
         else if (token.equals("("))
            floorIsGreaterThan = true;
         else if (token.equals("]"))
            ceilingIsLessThan = false;
         else if (token.equals(")"))
            ceilingIsLessThan = true;
         else if (token.equals(","))
            mid = true;
         else if (token.equals("\"") == false)
         {
            // A version token
            if (floor == null)
               floor = new Version(token);
            else
               ceiling = new Version(token);
         }

      }
      // check for parenthesis
      if (floorIsGreaterThan == null || ceilingIsLessThan == null)
      {
         // non-empty interval usage
         if (mid)
            throw new IllegalArgumentException("Missing parenthesis: " + rangeSpec);
         // single value
         floorIsGreaterThan = false;
         ceilingIsLessThan = false;
      }

      return new AbstractVersionRange(floor, ceiling, floorIsGreaterThan, ceilingIsLessThan);
   }

   /**
    * Create a new AbstractVersionRange.
    * 
    * @param floor the floor
    * @param ceiling the ceiling
    * @param floorIsLessThan whether the floor is <
    * @param ceilingIsLessThan whether the ceiling is <
    */
   public AbstractVersionRange(Version floor, Version ceiling, boolean floorIsLessThan, boolean ceilingIsLessThan)
   {
   }

   /**
    * Get the floor
    * 
    * @return the floor
    */
   public Version getFloor()
   {
      return null;
   }

   /**
    * Get the ceiling
    * 
    * @return the ceiling
    */
   public Version getCeiling()
   {
      return null;
   }

   /**
    * Test whether the version is in range
    * 
    * @return true when the version is in range
    */
   public boolean isInRange(Version v)
   {
      return false;
   }
}
