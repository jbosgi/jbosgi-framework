/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
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
package org.jboss.osgi.resolver;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.felix.framework.capabilityset.Attribute;
import org.apache.felix.framework.capabilityset.Directive;
import org.apache.felix.framework.util.FelixConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

/**
 * A ResolverBuilder
 * 
 * @author thomas.diesler@jboss.com
 * @since 01-Jun-2010
 */
public final class ModuleBuilder
{
   // Hide ctor
   private ModuleBuilder()
   {
   }

   @SuppressWarnings("rawtypes")
   public static Module createModule(Bundle bundle)
   {
      AbstractModule module = new AbstractModule(bundle);
      Map<String, String> headerMap = new HashMap<String, String>();
      Enumeration keys = bundle.getHeaders().keys();
      while (keys.hasMoreElements())
      {
         Object key = keys.nextElement();
         Object value = bundle.getHeaders().get(key);
         headerMap.put((String)key, (String)value);
      }

      // Add {@link ModuleCapability}
      String symbolicName = headerMap.get(Constants.BUNDLE_SYMBOLICNAME);
      module.addCapability(new AbstractModuleCapability(module, symbolicName));

      //
      // [TODO] Parse Fragment-Host.
      //
      // List<Requirement> hostReqs = parseFragmentHost(m_logger, owner, m_headerMap);

      //
      // Parse Require-Bundle
      //
      //List<ParsedHeaderClause> requireClauses = parseStandardHeader(headerMap.get(Constants.REQUIRE_BUNDLE));
      //requireClauses = normalizeRequireClauses(requireClauses);
      //List<Requirement> requireReqs = convertRequires(requireClauses, module);

      //
      // Parse Import-Package.
      //

      //List<ParsedHeaderClause> importClauses = parseStandardHeader((String)headerMap.get(Constants.IMPORT_PACKAGE));
      //importClauses = normalizeImportClauses(m_logger, importClauses, getManifestVersion());
      //List<Requirement> importReqs = convertImports(importClauses, owner);

      //
      // Parse DynamicImport-Package.
      //

      //List<ParsedHeaderClause> dynamicClauses = parseStandardHeader((String)headerMap.get(Constants.DYNAMICIMPORT_PACKAGE));
      //dynamicClauses = normalizeDynamicImportClauses(m_logger, dynamicClauses, getManifestVersion());
      //m_dynamicRequirements = convertImports(dynamicClauses, owner);

      //
      // Parse Export-Package.
      //

      // Get exported packages from bundle manifest.
      //List<ParsedHeaderClause> exportClauses = parseStandardHeader((String)headerMap.get(Constants.EXPORT_PACKAGE));
      //exportClauses = normalizeExportClauses(logger, exportClauses, getManifestVersion(), m_bundleSymbolicName, m_bundleVersion);
      //List<Capability> exportCaps = convertExports(exportClauses, owner);

      //
      // Calculate implicit imports.
      /*

      if (!getManifestVersion().equals("2"))
      {
         List<ParsedHeaderClause> implicitClauses = calculateImplicitImports(exportCaps, importClauses);
         importReqs.addAll(convertImports(implicitClauses, owner));

         List<ParsedHeaderClause> allImportClauses = new ArrayList<ParsedHeaderClause>(implicitClauses.size() + importClauses.size());
         allImportClauses.addAll(importClauses);
         allImportClauses.addAll(implicitClauses);

         exportCaps = calculateImplicitUses(exportCaps, allImportClauses);
      }
      */

      // Combine all capabilities.
      //m_capabilities = new ArrayList(capList.size() + exportCaps.size());
      //m_capabilities.addAll(capList);
      //m_capabilities.addAll(exportCaps);

      // Combine all requirements.
      //m_requirements = new ArrayList(importReqs.size() + requireReqs.size() + hostReqs.size());
      //m_requirements.addAll(importReqs);
      //m_requirements.addAll(requireReqs);
      //m_requirements.addAll(hostReqs);

      //
      // Parse Bundle-NativeCode.
      //

      // Get native library entry names for module library sources.
      //m_libraryClauses = parseLibraryStrings(m_logger, parseDelimitedString((String)m_headerMap.get(Constants.BUNDLE_NATIVECODE), ","));

      // Check to see if there was an optional native library clause, which is
      // represented by a null library header; if so, record it and remove it.
      //if ((m_libraryClauses.size() > 0) && (m_libraryClauses.get(m_libraryClauses.size() - 1).getLibraryEntries() == null))
      //{
      //   m_libraryHeadersOptional = true;
      //   m_libraryClauses.remove(m_libraryClauses.size() - 1);
      //}

      //
      // Parse activation policy.
      //

      // This sets m_activationPolicy, m_includedPolicyClasses, and
      // m_excludedPolicyClasses.
      //parseActivationPolicy(headerMap);
      //String exportPackage = (String)headers.get(Constants.EXPORT_PACKAGE);

      return module;
   }

   private static List<ParsedHeaderClause> normalizeRequireClauses(List<ParsedHeaderClause> clauses)
   {
      // Convert bundle version attribute to VersionRange type.
      for (int clauseIdx = 0; clauseIdx < clauses.size(); clauseIdx++)
      {
         for (int attrIdx = 0; attrIdx < clauses.get(clauseIdx).attrs.size(); attrIdx++)
         {
            Attribute attr = clauses.get(clauseIdx).attrs.get(attrIdx);
            if (attr.getName().equals(Constants.BUNDLE_VERSION_ATTRIBUTE))
            {
               clauses.get(clauseIdx).attrs.set(attrIdx,
                     new Attribute(Constants.BUNDLE_VERSION_ATTRIBUTE, VersionRange.parse(attr.getValue().toString()), attr.isMandatory()));
            }
         }
      }

      return clauses;
   }

   private static List<Requirement> convertRequires(List<ParsedHeaderClause> clauses, Module owner)
   {
      List<Requirement> reqList = new ArrayList<Requirement>();
      for (int clauseIdx = 0; clauseIdx < clauses.size(); clauseIdx++)
      {
         List<Attribute> attrs = clauses.get(clauseIdx).attrs;

         for (int pathIdx = 0; pathIdx < clauses.get(clauseIdx).paths.size(); pathIdx++)
         {
            // Prepend the symbolic name to the array of attributes.
            List<Attribute> newAttrs = new ArrayList<Attribute>(attrs.size() + 1);
            newAttrs.add(new Attribute(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE, clauses.get(clauseIdx).paths.get(pathIdx), false));
            newAttrs.addAll(attrs);

            // Create package requirement and add to requirement list.
            //reqList.add(new RequirementImpl(owner, Capability.MODULE_NAMESPACE, clauses.get(clauseIdx).dirs, newAttrs));
         }
      }

      return reqList;
   }

   // Like this: path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2,
   //            path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2
   private static List<ParsedHeaderClause> parseStandardHeader(String header)
   {
      List<ParsedHeaderClause> clauses = new ArrayList();

      if (header != null)
      {
         if (header.length() == 0)
         {
            throw new IllegalArgumentException("A header cannot be an empty string.");
         }

         List<String> clauseStrings = parseDelimitedString(header, FelixConstants.CLASS_PATH_SEPARATOR);

         for (int i = 0; (clauseStrings != null) && (i < clauseStrings.size()); i++)
         {
            clauses.add(parseStandardHeaderClause(clauseStrings.get(i)));
         }
      }

      return clauses;
   }

   // Like this: path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2
   private static ParsedHeaderClause parseStandardHeaderClause(String clauseString) throws IllegalArgumentException
   {
      // Break string into semi-colon delimited pieces.
      List<String> pieces = parseDelimitedString(clauseString, FelixConstants.PACKAGE_SEPARATOR);

      // Count the number of different paths; paths
      // will not have an '=' in their string. This assumes
      // that paths come first, before directives and
      // attributes.
      int pathCount = 0;
      for (int pieceIdx = 0; pieceIdx < pieces.size(); pieceIdx++)
      {
         if (pieces.get(pieceIdx).indexOf('=') >= 0)
         {
            break;
         }
         pathCount++;
      }

      // Error if no paths were specified.
      if (pathCount == 0)
      {
         throw new IllegalArgumentException("No paths specified in header: " + clauseString);
      }

      // Create an array of paths.
      List<String> paths = new ArrayList<String>(pathCount);
      for (int pathIdx = 0; pathIdx < pathCount; pathIdx++)
      {
         paths.add(pieces.get(pathIdx));
      }

      // Parse the directives/attributes.
      Map<String, Directive> dirsMap = new HashMap<String, Directive>();
      Map<String, Attribute> attrsMap = new HashMap<String, Attribute>();
      int idx = -1;
      String sep = null;
      for (int pieceIdx = pathCount; pieceIdx < pieces.size(); pieceIdx++)
      {
         // Check if it is a directive.
         if ((idx = pieces.get(pieceIdx).indexOf(FelixConstants.DIRECTIVE_SEPARATOR)) >= 0)
         {
            sep = FelixConstants.DIRECTIVE_SEPARATOR;
         }
         // Check if it is an attribute.
         else if ((idx = pieces.get(pieceIdx).indexOf(FelixConstants.ATTRIBUTE_SEPARATOR)) >= 0)
         {
            sep = FelixConstants.ATTRIBUTE_SEPARATOR;
         }
         // It is an error.
         else
         {
            throw new IllegalArgumentException("Not a directive/attribute: " + clauseString);
         }

         String key = pieces.get(pieceIdx).substring(0, idx).trim();
         String value = pieces.get(pieceIdx).substring(idx + sep.length()).trim();

         // Remove quotes, if value is quoted.
         if (value.startsWith("\"") && value.endsWith("\""))
         {
            value = value.substring(1, value.length() - 1);
         }

         // Save the directive/attribute in the appropriate array.
         if (sep.equals(FelixConstants.DIRECTIVE_SEPARATOR))
         {
            // Check for duplicates.
            if (dirsMap.get(key) != null)
            {
               throw new IllegalArgumentException("Duplicate directive: " + key);
            }
            dirsMap.put(key, new Directive(key, value));
         }
         else
         {
            // Check for duplicates.
            if (attrsMap.get(key) != null)
            {
               throw new IllegalArgumentException("Duplicate attribute: " + key);
            }
            attrsMap.put(key, new Attribute(key, value, false));
         }
      }

      List<Directive> dirs = new ArrayList<Directive>(dirsMap.size());
      for (Entry<String, Directive> entry : dirsMap.entrySet())
      {
         dirs.add(entry.getValue());
      }
      List<Attribute> attrs = new ArrayList<Attribute>(attrsMap.size());
      for (Entry<String, Attribute> entry : attrsMap.entrySet())
      {
         attrs.add(entry.getValue());
      }

      return null; //new ParsedHeaderClause(paths, dirs, attrs);
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
   public static List<String> parseDelimitedString(String value, String delim)
   {
      if (value == null)
      {
         value = "";
      }

      List<String> list = new ArrayList<String>();

      int CHAR = 1;
      int DELIMITER = 2;
      int STARTQUOTE = 4;
      int ENDQUOTE = 8;

      StringBuffer sb = new StringBuffer();

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
            throw new IllegalArgumentException("Invalid delimited string: " + value);
         }
      }

      if (sb.length() > 0)
      {
         list.add(sb.toString().trim());
      }

      return list;
   }

   class ParsedHeaderClause
   {
      public final List<String> paths;
      public final List<Directive> dirs;
      public final List<Attribute> attrs;

      public ParsedHeaderClause(List<String> paths, List<Directive> dirs, List<Attribute> attrs)
      {
         this.paths = paths;
         this.dirs = dirs;
         this.attrs = attrs;
      }
   }
}