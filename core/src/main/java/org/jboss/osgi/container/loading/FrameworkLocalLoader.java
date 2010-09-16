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
package org.jboss.osgi.container.loading;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.modules.LocalLoader;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.plugin.SystemPackagesPlugin;

/**
 * A {@link LocalLoader} that only loads framework defined classes/resources.
 *
 * @author thomas.diesler@jboss.com
 * @since 08-Jul-2010
 */
public class FrameworkLocalLoader extends SystemLocalLoader
{
   public FrameworkLocalLoader(BundleManager bundleManager)
   {
      super(getExportedPaths(bundleManager));
   }

   private static Set<String> getExportedPaths(BundleManager bundleManager)
   {
      Set<String> result = new LinkedHashSet<String>();

      // Add bootdelegation paths
      SystemPackagesPlugin plugin = bundleManager.getPlugin(SystemPackagesPlugin.class);
      List<String> bootDelegationPackages = plugin.getBootDelegationPackages();
      for (String packageName : bootDelegationPackages)
      {
         if (packageName.endsWith(".*"))
            packageName = packageName.substring(0, packageName.length() - 2);

         result.add(packageName.replace('.', File.separatorChar));
      }

      // Add system packages exported by the framework
      List<String> systemPackages = plugin.getSystemPackages();
      for (String packageSpec : systemPackages)
      {
         int index = packageSpec.indexOf(';');
         if (index > 0)
            packageSpec = packageSpec.substring(0, index);

         result.add(packageSpec.replace('.', File.separatorChar));
      }
      return result;
   }
}
