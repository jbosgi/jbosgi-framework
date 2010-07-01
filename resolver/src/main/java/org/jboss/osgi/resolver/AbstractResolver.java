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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A resolver Module.
 * 
 * @author thomas.diesler@jboss.com
 * @since 01-Jun-2010
 */
class AbstractResolver implements Resolver
{
   private Map<Long, Module> modules = new HashMap<Long, Module>();

   @Override
   public boolean resolve(List<Module> modules)
   {
      return false;
   }

   @Override
   public void installModule(Module module)
   {
      long bundleId = module.getBundle().getBundleId();
      if (modules.get(bundleId) != null)
         throw new IllegalArgumentException("Module already installed: " + module);
      modules.put(bundleId, module);
   }

   @Override
   public void uninstallModule(Module module)
   {
      long bundleId = module.getBundle().getBundleId();
      if (modules.remove(bundleId) == null)
         throw new IllegalArgumentException("Module not installed: " + module);
   }

   @Override
   public Module getModule(long bundleId)
   {
      return modules.get(bundleId);
   }

   @Override
   public List<Module> getModules(int bundleState)
   {
      List<Module> result = new ArrayList<Module>();
      for (Module aux : modules.values())
      {
         int auxState = aux.getBundle().getState();
         if ((auxState & bundleState) != 0)
            result.add(aux);
      }
      return Collections.unmodifiableList(result);
   }
}