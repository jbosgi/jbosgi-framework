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
package org.jboss.osgi.framework.bundle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.PackageAttribute;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

/**
 * A bundle validator for OSGi R4.
 * 
 * @author thomas.diesler@jboss.com
 * @version $Revision: 1.1 $
 */
public class BundleValidatorR4 implements BundleValidator
{
   private BundleManager bundleManager;
   
   public BundleValidatorR4(BundleManager bundleManager)
   {
      this.bundleManager = bundleManager;
   }

   @SuppressWarnings("deprecation")
   public void validateBundle(AbstractBundle bundleState) throws BundleException
   {
      OSGiMetaData osgiMetaData = bundleState.getOSGiMetaData();
      
      // Missing Bundle-SymbolicName
      String symbolicName = bundleState.getSymbolicName();
      if (symbolicName == null)
         throw new BundleException("Missing Bundle-SymbolicName in: " + bundleState);
      
      // Bundle-ManifestVersion value not equal to 2, unless the Framework specifically recognizes the semantics of a later release.
      int manifestVersion = osgiMetaData.getBundleManifestVersion();
      if (manifestVersion > 2)
         throw new BundleException("Unsupported manifest version " + manifestVersion + " for " + bundleState);

      // [TODO] Duplicate attribute or duplicate directive (except in the Bundle-Native code clause).
      
      // Multiple imports of a given package.
      List<PackageAttribute> importPackages = osgiMetaData.getImportPackages();
      if (importPackages != null && importPackages.isEmpty() == false)
      {
         Set<String> packages = new HashSet<String>();
         for (PackageAttribute packageAttribute : importPackages)
         {
            String packageName = packageAttribute.getAttribute();
            if (packages.contains(packageName))
               throw new BundleException("Duplicate import of package " + packageName + " for " + bundleState);
            packages.add(packageName);

            if (packageName.startsWith("java."))
               throw new BundleException("Not allowed to import java.* for " + bundleState);

            String version = packageAttribute.getAttributeValue(Constants.VERSION_ATTRIBUTE, String.class);
            String specificationVersion = packageAttribute.getAttributeValue(Constants.PACKAGE_SPECIFICATION_VERSION, String.class);
            if (version != null && specificationVersion != null && version.equals(specificationVersion) == false)
               throw new BundleException(packageName + " version and specification version should be the same for " + bundleState);
         }
      }
      
      // Export or import of java.*.
      List<PackageAttribute> exportPackages = osgiMetaData.getExportPackages();
      if (exportPackages != null && exportPackages.isEmpty() == false)
      {
         for (PackageAttribute packageAttribute : exportPackages)
         {
            String packageName = packageAttribute.getAttribute();
            if (packageName.startsWith("java."))
               throw new BundleException("Not allowed to export java.* for " + bundleState);
         }
      }
      
      // [TODO] Export-Package with a mandatory attribute that is not defined.
      
      // Installing a bundle that has the same symbolic name and version as an already installed bundle.
      for (AbstractBundle bundle : bundleManager.getBundles())
      {
         OSGiMetaData other = bundle.getOSGiMetaData();
         if (symbolicName.equals(other.getBundleSymbolicName()))
         {
            if (other.isSingleton() && osgiMetaData.isSingleton())
               throw new BundleException("Cannot install singleton " + bundleState + " another singleton is already installed: " + bundle.getLocation());
            if (other.getBundleVersion().equals(osgiMetaData.getBundleVersion()))
               throw new BundleException("Cannot install " + bundleState + " a bundle with that name and version is already installed: "
                     + bundle.getLocation());
         }
      }
      
      // [TODO] Updating a bundle to a bundle that has the same symbolic name and version as another installed bundle.
      
      // [TODO] Any syntactic error (for example, improperly formatted version or bundle symbolic name, unrecognized directive value, etc.).
      
      // [TODO] Specification-version and version specified together (for the same package(s)) but with different values.
      
      // [TODO] The manifest lists a OSGI-INF/permission.perm file but no such file is present.
      
      // [TODO] Requiring the same bundle symbolic name more than once
   }
}
