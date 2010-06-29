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
package org.jboss.osgi.framework.metadata;

import java.net.URL;
import java.util.Dictionary;
import java.util.List;

/**
 * OSGi specific meta data.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 * @author thomas.diesler@jboss.com
 */
public interface OSGiMetaData 
{
   /**
    * Get the headers in raw unlocalized format.
    * 
    * @return the headers
    */
   Dictionary<String, String> getHeaders();
   
   /**
    * Extension point to read custom manifest headers.
    *
    * @param key the header key
    * @return value or null of no such header
    */
   String getHeader(String key);

   /**
    * Get bundle activation policy.
    *
    * @return bundle activation policy
    */
   ActivationPolicyMetaData getBundleActivationPolicy();

   /**
    * Get bundle activator class name.
    *
    * @return bundle activator classname or null if no such attribute
    */
   String getBundleActivator();

   /**
    * Get the bundle category
    *
    * @return list of category names
    */
   List<String> getBundleCategory();

   /**
    * Get the bundle classpath
    *
    * @return list of JAR file path names or directories inside bundle
    */
   List<String> getBundleClassPath();

   /**
    * Get the description
    *
    * @return a description
    */
   String getBundleDescription();

   /**
    * Get the localization's location
    *
    * @return location in the bundle for localization files
    */
   String getBundleLocalization();

   /**
    * Get the bundle manifest version
    *
    * @return bundle's specification number
    */
   int getBundleManifestVersion();

   /**
    * Get the name
    *
    * @return readable name
    */
   String getBundleName();

   /**
    * Get native code libs
    * @return native libs contained in the bundle
    */
   List<ParameterizedAttribute> getBundleNativeCode();

   /**
    * Get required exectuion envs
    *
    * @return list of execution envs that must be present on the Service Platform
    */
   List<String> getRequiredExecutionEnvironment();

   /**
    * Get bundle symbolic name.
    *
    * @return bundle's symbolic name
    */
   String getBundleSymbolicName();

   /**
    * Get the bundle parameters
    * 
    * @return the bundle parameters
    */
   ParameterizedAttribute getBundleParameters();

   /**
    * Get the update url.
    *
    * @return URL of an update bundle location
    */
   URL getBundleUpdateLocation();

   /**
    * Get bundle's version.
    * 
    * Note, R3 does not define a specific syntax for Bundle-Version.
    * 
    * @return version of this bundle
    */
   String getBundleVersion();

   /**
    * Get dynamic imports.
    *
    * @return package names that should be dynamically imported when needed
    */
   List<PackageAttribute> getDynamicImports();

   /**
    * Get the export packages.
    *
    * @return exported packages
    */
   List<PackageAttribute> getExportPackages();

   /**
    * Get the fragment host.
    *
    * @return host bundle for this fragment
    */
   ParameterizedAttribute getFragmentHost();

   /**
    * Get the import packages.
    *
    * @return imported packages.
    */
   List<PackageAttribute> getImportPackages();

   /**
    * Get the required exports
    *
    * @return required exports from anoter bundle
    */
   List<ParameterizedAttribute> getRequireBundles();
   
   /**
    * Whether the bundle is a singleton
    * 
    * @return true when it is a singleton
    */
   boolean isSingleton();
   
   /**
    * Get the fragment attrachment
    * 
    * todo fragments
    * @return the fragment attachment
    */
   String getFragmentAttachment();

   /**
    * Returns the initial start level of the bundle.
    * @return The initial start level of the bundle or -1 when the initial
    * bundle start level has not been defined for this bundle.
    */
   int getInitialStartLevel();
}
