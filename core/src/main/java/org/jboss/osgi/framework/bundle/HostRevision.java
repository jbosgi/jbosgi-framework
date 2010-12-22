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

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.BundleException;

/**
 * A {@link HostRevision} is responsible for the classloading and resource loading of a bundle.
 *
 * It is associated with a {@link XModule} which holds the wiring information of the bundle.<p/>
 *
 * Every time a bundle is updated a new {@link HostRevision} is created and referenced
 * from the {@link HostBundle}.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
public class HostRevision extends AbstractUserRevision
{
   static final Logger log = Logger.getLogger(HostRevision.class);

   private List<FragmentRevision> attachedFragments;

   public HostRevision(HostBundle hostBundle, Deployment dep) throws BundleException
   {
      super(hostBundle, dep);
   }

   @Override
   public HostBundle getBundleState()
   {
      return (HostBundle)super.getBundleState();
   }

   @Override
   void refreshRevisionInternal(XModule resModule)
   {
      // Attach the host bundle
      resModule.addAttachment(HostBundle.class, getBundleState());
      attachedFragments = null;
   }

   public void attachFragment(FragmentRevision fragRev)
   {
      if (attachedFragments == null)
         attachedFragments = new CopyOnWriteArrayList<FragmentRevision>();

      attachedFragments.add(fragRev);
   }

   public List<FragmentRevision> getAttachedFragments()
   {
      if (attachedFragments == null)
         return Collections.emptyList();

      return Collections.unmodifiableList(attachedFragments);
   }

   @Override
   public Class<?> loadClass(String className) throws ClassNotFoundException
   {
      getBundleState().assertNotUninstalled();

      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      if (getBundleState().ensureResolved(false) == false)
         throw new ClassNotFoundException("Class '" + className + "' not found in: " + this);

      // Load the class through the module
      ModuleClassLoader loader = getModuleClassLoader();
      return loader.loadClass(className, true);
   }

   @Override
   public URL getResource(String path)
   {
      getBundleState().assertNotUninstalled();

      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      if (getBundleState().ensureResolved(true) == true)
         return getModuleClassLoader().getResource(path);

      // If this bundle cannot be resolved, then only this bundle must be searched for the specified resource
      return getEntry(path);
   }

   @Override
   public Enumeration<URL> getResources(String path) throws IOException
   {
      getBundleState().assertNotUninstalled();

      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      if (getBundleState().ensureResolved(true) == true)
      {
         Enumeration<URL> resources = getModuleClassLoader().getResources(path);
         return resources.hasMoreElements() ? resources : null;
      }

      // If this bundle cannot be resolved, then only this bundle must be searched for the specified resource
      for (VirtualFile rootFile : getContentRoots())
      {
         try
         {
            VirtualFile child = rootFile.getChild(path);
            if (child == null)
               return null;

            Vector<URL> vector = new Vector<URL>();
            vector.add(child.toURL());
            return vector.elements();
         }
         catch (IOException ex)
         {
            log.errorf(ex, "Cannot get resources: %s", path);
            return null;
         }
      }

      return null;
   }

   @Override
   URL getLocalizationEntry(String path)
   {
      // The framework must first search in the bundle’s JAR for the localization entry.
      URL entry = getEntry(path);
      if (entry != null)
         return entry;

      // If the entry is not found and the bundle has fragments,
      // then the attached fragment JARs must be searched for the localization entry.
      for (FragmentRevision fragrev : getAttachedFragments())
      {
         if (fragrev.getBundleState().isUninstalled() == false)
         {
            entry = fragrev.getEntry(path);
            if (entry != null)
               return entry;
         }
      }

      return null;
   }
}
