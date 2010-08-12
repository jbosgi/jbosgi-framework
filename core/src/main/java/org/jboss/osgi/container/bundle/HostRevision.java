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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.osgi.container.loading.ModuleClassLoaderExt;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.AbstractVFS;
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
public class HostRevision extends AbstractRevision
{
   static final Logger log = Logger.getLogger(HostRevision.class);

   private final List<VirtualFile> contentRoots;
   private List<FragmentRevision> attachedFragments;

   public HostRevision(HostBundle hostBundle, Deployment dep, int revision) throws BundleException
   {
      super(hostBundle, dep, revision);

      // Set the aggregated root file
      contentRoots = getBundleClassPath(dep.getRoot(), getOSGiMetaData());
   }

   public List<VirtualFile> getContentRoots()
   {
      return contentRoots;
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
      if (getBundleState().ensureResolved() == false)
         throw new ClassNotFoundException("Class '" + className + "' not found in: " + this);

      // Load the class through the module
      ModuleClassLoader loader = getModuleClassLoader();
      return loader.loadClass(className);
   }

   @Override
   public URL getResource(String path)
   {
      getBundleState().assertNotUninstalled();

      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      if (getBundleState().ensureResolved() == true)
         return getModuleClassLoader().getResource(path);

      // If this bundle cannot be resolved, then only this bundle must be searched for the specified resource
      return getEntry(path);
   }

   @Override
   public Enumeration<URL> getResources(String path) throws IOException
   {
      getBundleState().assertNotUninstalled();

      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      if (getBundleState().ensureResolved() == true)
      {
         Enumeration<URL> resources = getModuleClassLoader().getResources(path);
         return resources.hasMoreElements() ? resources : null;
      }

      // If this bundle cannot be resolved, then only this bundle must be searched for the specified resource
      try
      {
         VirtualFile child = getContentRoot().getChild(path);
         if (child == null)
            return null;

         Vector<URL> vector = new Vector<URL>();
         vector.add(child.toURL());
         return vector.elements();
      }
      catch (IOException ex)
      {
         log.error("Cannot get resources: " + path, ex);
         return null;
      }
   }

   private List<VirtualFile> getBundleClassPath(VirtualFile rootFile, OSGiMetaData metadata)
   {
      if (rootFile == null)
         throw new IllegalArgumentException("Null rootFile");
      
      List<VirtualFile> rootList;

      // Add the Bundle-ClassPath to the root virtual files
      if (metadata.getBundleClassPath().size() > 0)
      {
         rootList = new ArrayList<VirtualFile>();
         for (String path : metadata.getBundleClassPath())
         {
            if (path.equals("."))
            {
               rootList.add(rootFile);
            }
            else
            {
               try
               {
                  VirtualFile child = rootFile.getChild(path);
                  if (child != null)
                  {
                     VirtualFile root = AbstractVFS.getRoot(child.toURL());
                     rootList.add(root);
                  }
               }
               catch (IOException ex)
               {
                  log.error("Cannot get class path element: " + path, ex);
               }
            }
         }
      }
      else
      {
         rootList = new ArrayList<VirtualFile>(Collections.singleton(rootFile));
      }
      return Collections.unmodifiableList(rootList);
   }

   public void attachFragment(FragmentRevision fragRev)
   {
      if (attachedFragments == null)
         attachedFragments = new CopyOnWriteArrayList<FragmentRevision>();
      
      ModuleClassLoaderExt classLoader = (ModuleClassLoaderExt)getModuleClassLoader();
      classLoader.attachFragment(fragRev);
      
      attachedFragments.add(fragRev);
   }
}
