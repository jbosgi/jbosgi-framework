/*
* JBoss, Home of Professional Open Source
* Copyright 2008, JBoss Inc., and individual contributors as indicated
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
package org.jboss.osgi.msc.metadata;

// $Id$

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Meta data for native code libraries as defined by OSGi R4V42.  
 * 
 * 3.9 Loading Native Code Libraries
 * http://www.osgi.org/Download/File?url=/download/r4v42/r4.core.pdf
 * 
 * @author thomas.diesler@jboss.com
 * @version $Revision$
 * @since 21-Jan-2010
 */
public class NativeLibrary implements Serializable
{
   /** The serialVersionUID */
   private static final long serialVersionUID = -1637806718398794304L;

   private List<String> osNames = new ArrayList<String>();
   private String librarySource;
   private String libraryPath;
   private List<String> processors = new ArrayList<String>();
   private List<VersionRange> osVersions = new ArrayList<VersionRange>();
   private List<String> languages = new ArrayList<String>();
   private String selectionFilter;
   private boolean optional;

   /**
    * Create a NativeCode instance with mandatory properties.
    * @param osNames The set of OS names 
    * @param libraryPath The library path
    * @param librarySource An interface from which to retrieve the actual library location
    */
   public NativeLibrary(List<String> osNames, String libraryPath, String librarySource)
   {
      if (libraryPath == null)
         throw new IllegalArgumentException("Null library path: " + libraryPath);
      if (osNames == null || osNames.isEmpty())
         throw new IllegalArgumentException("Illegal OS names: " + osNames);
      if (librarySource == null)
         throw new IllegalArgumentException("Null file privider: " + librarySource);

      this.osNames = osNames;
      this.libraryPath = libraryPath;
      this.librarySource = librarySource;
   }

   public String getLibrarySource()
   {
      return librarySource;
   }

   public String getLibraryPath()
   {
      return libraryPath;
   }

   public List<String> getOsNames()
   {
      return Collections.unmodifiableList(osNames);
   }

   public List<VersionRange> getOsVersions()
   {
      return Collections.unmodifiableList(osVersions);
   }

   public void setOsVersions(List<VersionRange> osVersions)
   {
      this.osVersions = osVersions;
   }

   public List<String> getProcessors()
   {
      return Collections.unmodifiableList(processors);
   }

   public void setProcessors(List<String> processors)
   {
      this.processors = processors;
   }

   public void setLanguages(List<String> languages)
   {
      this.languages = languages;
   }
   
   public List<String> getLanguages()
   {
      return Collections.unmodifiableList(languages);
   }

   public String getSelectionFilter()
   {
      return selectionFilter;
   }

   public void setSelectionFilter(String selectionFilter)
   {
      this.selectionFilter = selectionFilter;
   }

   public boolean isOptional()
   {
      return optional;
   }

   public void setOptional(boolean optional)
   {
      this.optional = optional;
   }
}
