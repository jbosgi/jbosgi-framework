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
package org.jboss.osgi.container.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

import org.jboss.osgi.container.plugin.SystemPackagesPlugin;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.internal.OSGiManifestMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/**
 * The system bundle
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
public class SystemBundle extends AbstractBundle implements Revision
{
   private OSGiMetaData metadata;
   private XModule resolverModule;
   
   public SystemBundle(BundleManager bundleManager)
   {
      super(bundleManager, Constants.SYSTEM_BUNDLE_SYMBOLICNAME);

      // Initialize basic metadata
      Manifest manifest = new Manifest();
      Attributes attributes = manifest.getMainAttributes();
      attributes.put(new Name(Constants.BUNDLE_SYMBOLICNAME), Constants.SYSTEM_BUNDLE_SYMBOLICNAME);
      metadata = new OSGiManifestMetaData(manifest);
      
      // Initialize the system resolver module
      // [TODO] Bring the resolver module in sync with the metadata
      XModuleBuilder builder = XResolverFactory.getModuleBuilder();
      resolverModule = builder.createModule(getGlobalRevisionId(), getSymbolicName(), getVersion());
      resolverModule.addAttachment(Bundle.class, this);
      
      builder.addBundleCapability(getSymbolicName(), getVersion());
      
      SystemPackagesPlugin plugin = getBundleManager().getPlugin(SystemPackagesPlugin.class);
      List<String> systemPackages = plugin.getSystemPackages(true);
      if (systemPackages.isEmpty() == true)
         throw new IllegalStateException("Framework system packages not available");
      
      // Construct framework capabilities from system packages
      for (String packageSpec : systemPackages)
      {
         String packname = packageSpec;
         Version version = Version.emptyVersion;

         int versionIndex = packname.indexOf(";version=");
         if (versionIndex > 0)
         {
            packname = packageSpec.substring(0, versionIndex);
            version = Version.parseVersion(packageSpec.substring(versionIndex + 9));
         }

         Map<String, Object> attrs = new HashMap<String, Object>();
         attrs.put(Constants.VERSION_ATTRIBUTE, version);
         
         builder.addPackageCapability(packname, null, attrs);
      }

      // Add the system bundle
      bundleManager.addBundleState(this);
   }

   /**
    * Assert that the given bundle is an instance of SystemBundle
    * @throws IllegalArgumentException if the given bundle is not an instance of SystemBundle
    */
   public static SystemBundle assertBundleState(Bundle bundle)
   {
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
      
      if (bundleState instanceof SystemBundle == false)
         throw new IllegalArgumentException("Not a SystemBundle: " + bundleState);

      return (SystemBundle)bundleState;
   }

   @Override
   public OSGiMetaData getOSGiMetaData()
   {
      return metadata;
   }

   @Override
   public XModule getResolverModule()
   {
      return resolverModule;
   }


   @Override
   public List<XModule> getAllResolverModules()
   {
      return Collections.singletonList(resolverModule);
   }

   @Override
   public String getLocation()
   {
      return Constants.SYSTEM_BUNDLE_LOCATION;
   }

   @Override
   public void addToResolver()
   {
      getResolverPlugin().addModule(getResolverModule());
   }

   @Override
   public boolean ensureResolved()
   {
      // The system bundle is always resolved
      return true;
   }

   @Override
   public void removeFromResolver()
   {
      getResolverPlugin().removeModule(getResolverModule());
   }

   @Override
   AbstractBundleContext createContextInternal()
   {
      return new SystemBundleContext(this);
   }

   @Override
   void startInternal(int options) throws BundleException
   {
      createBundleContext();
   }

   @Override
   void stopInternal(int options) throws BundleException
   {
      destroyBundleContext();
   }

   @Override
   void updateInternal(InputStream input) throws BundleException
   {
      throw new NotImplementedException();
   }

   @Override
   void uninstallInternal() throws BundleException
   {
      throw new NotImplementedException();
   }

   @Override
   public Class<?> loadClass(String name) throws ClassNotFoundException
   {
      ClassLoader classLoader = getClass().getClassLoader();
      return classLoader.loadClass(name);
   }

   @Override
   public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse)
   {
      // [Bug-1472] Clarify the semantic of resource API when called on the system bundle
      // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1472
      return null;
   }

   @Override
   public URL getEntry(String path)
   {
      // [Bug-1472] Clarify the semantic of resource API when called on the system bundle
      // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1472
      return null;
   }

   @Override
   @SuppressWarnings({ "rawtypes" })
   public Enumeration getEntryPaths(String path)
   {
      // [Bug-1472] Clarify the semantic of resource API when called on the system bundle
      // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1472
      return null;
   }

   @Override
   URL getLocalizationEntry(String entryPath)
   {
      return null;
   }

   @Override
   public URL getResource(String name)
   {
      return getClass().getClassLoader().getResource(name);
   }

   @Override
   @SuppressWarnings({ "rawtypes" })
   public Enumeration getResources(String name) throws IOException
   {
      return getClass().getClassLoader().getResources(name);
   }

   @Override
   public int getGlobalRevisionId()
   {
      // The system bundle has revision ID 0
      return 0;
   }

   @Override
   public int getUpdateCount()
   {
      // There is only 1 revision from the system bundle
      return 0;
   }

   @Override
   public Version getVersion()
   {
      return Version.emptyVersion;
   }
}
