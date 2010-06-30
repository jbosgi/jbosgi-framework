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
package org.jboss.osgi.msc.loading;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.modules.AssertionSetting;
import org.jboss.modules.Module;
import org.jboss.modules.Module.Flag;
import org.jboss.modules.ModuleClassLoader;

/**
 * A {@link ModuleClassLoader} that can load the framework system classes.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class FrameworkModuleClassLoader extends ModuleClassLoader
{
   private ClassLoader delegateLoader;
   
   public FrameworkModuleClassLoader(Module module, ClassLoader delegateLoader)
   {
      super(module, Collections.<Flag>emptySet(), AssertionSetting.INHERIT, null);
      this.delegateLoader = delegateLoader;
      if (delegateLoader == null)
         throw new IllegalArgumentException("Null systemLoader");
   }

   @Override
   protected Class<?> findClass(String className, boolean exportsOnly) throws ClassNotFoundException
   {
      Class<?> result = delegateLoader.loadClass(className);
      return result;
   }

   public Set<String> getExportedPaths()
   {
      Set<String> exportedPaths = new LinkedHashSet<String>();
      exportedPaths.add("org/osgi/framework");
      return Collections.unmodifiableSet(exportedPaths);
   }
}
