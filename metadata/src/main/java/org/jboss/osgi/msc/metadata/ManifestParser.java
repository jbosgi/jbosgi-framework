/*
* JBoss, Home of Professional Open Source
* Copyright 2009, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
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

// This class is based on some original classes from
// Apache Felix which is licensed as below

/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.osgi.msc.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.osgi.msc.metadata.internal.AbstractPackageAttribute;
import org.jboss.osgi.msc.metadata.internal.AbstractParameter;
import org.jboss.osgi.msc.metadata.internal.AbstractParameterizedAttribute;

/**
 * ManifestParser.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 1.1 $
 */
public class ManifestParser
{
   /**
    * Parse packages
    * 
    * @param header the header
    * @param list the list of packages to create
    */
   public static void parsePackages(String header, List<PackageAttribute> list)
   {
      parse(header, list, true);
   }

   /**
    * Parse parameters
    * 
    * @param header the header
    * @param list the list of parameters to create
    */
   public static void parseParameters(String header, List<ParameterizedAttribute> list)
   {
      parse(header, list, false);
   }

   /**
    * Parse paths
    * 
    * @param header the header
    * @param list the list of paths to create
    */
   public static void parsePaths(String header, List<ParameterizedAttribute> list)
   {
      parse(header, list, false);
   }

   /**
    * Parse a header
    * 
    * @param header the header
    * @param list the list to create
    * @param packages whether to create packages
    */
   @SuppressWarnings({ "rawtypes", "unchecked" })
   public static void parse(String header, List list, boolean packages)
   {
      if (header == null)
         return;
      if (header.length() == 0)
         throw new IllegalArgumentException("Empty header");
      
      // Split the header into clauses using which are seperated by commas
      // Like this: path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2,
      //            path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2
      List<String> clauses = parseDelimitedString(header, ",");
      
      // Now parse each clause
      for (String clause : clauses)
      {
         // Split the cause into paths, directives and attributes using the semi-colon
         // Like this: path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2
         List<String> pieces = parseDelimitedString(clause, ";");

         // Collect the paths they should be first
         List<String> paths = new ArrayList<String>();
         for (String piece : pieces)
         {
            if (piece.indexOf('=') >= 0)
               break;
            paths.add(unquote(piece));
         }
         if (paths.isEmpty())
            throw new IllegalArgumentException("No paths for " + clause);
         
         Map<String, Parameter> directives = null;
         Map<String, Parameter> attributes = null;

         for (int i = paths.size(); i < pieces.size(); ++i)
         {
            String piece = pieces.get(i);
            int seperator = piece.indexOf(":=");
            if (seperator >= 0)
            {
               String name = piece.substring(0, seperator);
               String value = piece.substring(seperator + 2);
               if (directives == null)
                  directives = new HashMap<String, Parameter>();
               String unquoted = unquote(name);
               if (directives.containsKey(unquoted))
                  throw new IllegalStateException("Dupicate directive: " + unquoted);
               directives.put(unquoted, new AbstractParameter(unquote(value)));
            }
            else
            {
               seperator = piece.indexOf("=");
               if (seperator >= 0)
               {
                  String name = piece.substring(0, seperator);
                  String value = piece.substring(seperator + 1);
                  if (attributes == null)
                     attributes = new HashMap<String, Parameter>();
                  String unquoted = unquote(name);
                  if (attributes.containsKey(unquoted))
                     throw new IllegalStateException("Dupicate attribute: " + unquoted);
                  attributes.put(unquoted, new AbstractParameter(unquote(value)));
               }
               else
               {
                  throw new IllegalArgumentException("Path " + piece + " should appear before attributes and directives in " + clause);
               }
            }
         }
         
         for (String path : paths)
         {
            ParameterizedAttribute metadata = null;
            if (packages)
               metadata = new AbstractPackageAttribute(path, attributes, directives);
            else
               metadata = new AbstractParameterizedAttribute(path, attributes, directives);
            
            list.add(metadata);
         }
    
      }
   }
   
   /**
    * Remove around quotes around a string
    * 
    * @param string the string
    * @return the unquoted string
    */
   private static String unquote(String string)
   {
      if (string.length() < 2)
         return string;
      if (string.charAt(0) == '\"' && string.charAt(string.length()-1) == '\"')
         return string.substring(1, string.length()-1);
      return string;
   }

   /**
    * Parses delimited string and returns an array containing the tokens. This
    * parser obeys quotes, so the delimiter character will be ignored if it is
    * inside of a quote. This method assumes that the quote character is not
    * included in the set of delimiter characters.
    * @param value the delimited string to parse.
    * @param delim the characters delimiting the tokens.
    * @return an array of string tokens or null if there were no tokens.
   **/
   private static List<String> parseDelimitedString(String value, String delim)
   {
      if (value == null)
         value = "";

      List<String> list = new ArrayList<String>();

      int CHAR = 1;
      int DELIMITER = 2;
      int STARTQUOTE = 4;
      int ENDQUOTE = 8;

      StringBuilder sb = new StringBuilder();

      int expecting = (CHAR | DELIMITER | STARTQUOTE);

      for (int i = 0; i < value.length(); i++)
      {
         char c = value.charAt(i);

         boolean isDelimiter = (delim.indexOf(c) >= 0);
         boolean isQuote = (c == '"');

         if (isDelimiter && ((expecting & DELIMITER) > 0))
         {
            list.add(sb.toString().trim());
            sb.delete(0, sb.length());
            expecting = (CHAR | DELIMITER | STARTQUOTE);
         }
         else if (isQuote && ((expecting & STARTQUOTE) > 0))
         {
            sb.append(c);
            expecting = CHAR | ENDQUOTE;
         }
         else if (isQuote && ((expecting & ENDQUOTE) > 0))
         {
            sb.append(c);
            expecting = (CHAR | STARTQUOTE | DELIMITER);
         }
         else if ((expecting & CHAR) > 0)
         {
            sb.append(c);
         }
         else
         {
            throw new IllegalArgumentException("Invalid delimited string: '" + value + "' delimiter=" + delim);
         }
      }

      if (sb.length() > 0)
         list.add(sb.toString().trim());

      return list;
   }

}
