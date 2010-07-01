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
package org.jboss.osgi.felix.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Permission;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.framework.capabilityset.Capability;
import org.apache.felix.framework.capabilityset.Requirement;
import org.apache.felix.framework.resolver.Content;
import org.apache.felix.framework.resolver.FragmentRequirement;
import org.apache.felix.framework.resolver.Module;
import org.apache.felix.framework.resolver.Wire;
import org.apache.felix.framework.util.manifestparser.R4Library;
import org.jboss.logging.Logger;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/**
 * An implementation of the ModuleExtension.
 * 
 * This implemantion should use no framework specific API.
 * It is the extension point for a framework specific Module.
 *  
 * @author thomas.diesler@jboss.com
 * @since 31-May-2010
 */
public abstract class AbstractModule implements Module
{
   // Provide logging
   final Logger log = Logger.getLogger(AbstractModule.class);

   private Bundle bundle;
   private Map<String, String> headerMap;
   private List<Capability> capabilities;
   private List<Requirement> requirements;
   private List<Requirement> dynamicreqs;
   private List<Module> fragments;
   private List<Wire> wires;
   private boolean resolved;

   public AbstractModule(Bundle bundle)
   {
      this.bundle = bundle;
   }

   @Override
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public Map getHeaders()
   {
      if (headerMap == null)
      {
         headerMap = new HashMap<String, String>();
         Dictionary<String, String> headers = bundle.getHeaders();
         Enumeration<String> keys = headers.keys();
         while (keys.hasMoreElements())
         {
            String key = keys.nextElement();
            String value = headers.get(key);
            headerMap.put(key, value);
         }
      }
      return Collections.unmodifiableMap(headerMap);
   }

   @Override
   public boolean isExtension()
   {
      return false;
   }

   @Override
   public String getSymbolicName()
   {
      return bundle.getSymbolicName();
   }

   @Override
   public Version getVersion()
   {
      return bundle.getVersion();
   }

   @Override
   public boolean isStale()
   {
      return false;
   }

   @Override
   public boolean isRemovalPending()
   {
      return false;
   }

   @Override
   public List<Capability> getCapabilities()
   {
      if (capabilities == null)
         capabilities = createCapabilities();

      return capabilities;
   }

   protected abstract List<Capability> createCapabilities();

   @Override
   public List<Requirement> getRequirements()
   {
      if (requirements == null)
         requirements = createRequirements();

      return requirements;
   }

   protected abstract List<Requirement> createRequirements();

   @Override
   public List<Requirement> getDynamicRequirements()
   {
      if (dynamicreqs == null)
         dynamicreqs = createDynamicRequirements();

      return requirements;
   }

   protected abstract List<Requirement> createDynamicRequirements();


   @Override
   public List<R4Library> getNativeLibraries()
   {
      throw new NotImplementedException();
   }

   @Override
   public int getDeclaredActivationPolicy()
   {
      throw new NotImplementedException();
   }

   @Override
   public Bundle getBundle()
   {
      return bundle;
   }

   @Override
   public String getId()
   {
      return bundle.getLocation();
   }

   @Override
   public List<Wire> getWires()
   {
      return (wires != null ? Collections.unmodifiableList(wires) : null);
   }

   @Override
   public void setWires(List<Wire> wires)
   {
      this.wires = wires;
   }

   @Override
   public void removeWires()
   {
      this.wires = null;
   }

   @Override
   public void attachFragments(List<Module> modules) throws Exception
   {
      fragments = modules;
      capabilities = null;
      requirements = null;
   }

   @Override
   public void removeFragments()
   {
      fragments = null;
      capabilities = null;
      requirements = null;
   }

   @Override
   public List<Module> getFragments()
   {
      if (fragments == null)
         return Collections.emptyList();
      
      return fragments;
   }

   @Override
   public boolean isResolved()
   {
      return resolved;
   }

   @Override
   public void setResolved()
   {
      resolved = true;
   }

   @Override
   public ProtectionDomain getSecurityContext()
   {
      throw new NotImplementedException();
   }

   @Override
   public boolean impliesDirect(Permission permission)
   {
      return true;
   }

   @Override
   public Content getContent()
   {
      throw new NotImplementedException();
   }

   @Override
   public Class<?> getClassByDelegation(String name) throws ClassNotFoundException
   {
      return bundle.loadClass(name);
   }

   @Override
   public URL getResourceByDelegation(String name)
   {
      return bundle.getResource(name);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration getResourcesByDelegation(String name)
   {
      // TODO: why doesn't this throw an IOException
      try
      {
         return bundle.getResources(name);
      }
      catch (IOException ex)
      {
         return null;
      }
   }

   @Override
   public URL getEntry(String name)
   {
      return bundle.getEntry(name);
   }

   @Override
   public boolean hasInputStream(int index, String urlPath) throws IOException
   {
      throw new NotImplementedException();
   }

   @Override
   public InputStream getInputStream(int index, String urlPath) throws IOException
   {
      throw new NotImplementedException();
   }

   /** 
    * Gets the potential wire for a given requirement.
    * @return The wire or null 
    */
   public Wire getWireForRequirement(Requirement requirement)
   {
      Wire result = null;
      if (wires != null)
      {
         for (Wire aux : wires)
         {
            Requirement auxreq = aux.getRequirement();
            if (auxreq instanceof FragmentRequirement)
                auxreq = ((FragmentRequirement)auxreq).getRequirement();
            
            if (auxreq.equals(requirement))
            {
               result = aux;
               break;
            }
         }
      }
      return result;
   }

   @Override
   public String toString()
   {
      return "Module[" + getBundle() + "]";
   }
}