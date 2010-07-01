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
import java.util.List;

import org.osgi.framework.Bundle;


/**
 * A resolver Module.
 * 
 * @author thomas.diesler@jboss.com
 * @since 01-Jun-2010
 */
class AbstractModule implements Module
{
   private Bundle bundle;
   private List<Requirement> requirements;
   private List<Capability> capabilities;
   private List<Wire> wires;
   private boolean resolved;

   AbstractModule(Bundle bundle)
   {
      this.bundle = bundle;
   }

   @Override
   public Bundle getBundle()
   {
      return bundle;
   }

   void addRequirement(Requirement requirement)
   {
      if (requirements == null)
         requirements = new ArrayList<Requirement>();
      requirements.add(requirement);
   }
   
   @Override
   public List<Requirement> getRequirements()
   {
      return Collections.unmodifiableList(requirements);
   }

   void addCapability(Capability capability)
   {
      if (capabilities == null)
         capabilities = new ArrayList<Capability>();
      capabilities.add(capability);
   }
   
   @Override
   public List<Capability> getCapabilities()
   {
      return Collections.unmodifiableList(capabilities);
   }

   void addWire(Wire wire)
   {
      if (wires == null)
         wires = new ArrayList<Wire>();
      wires.add(wire);
   }
   
   @Override
   public List<Wire> getWires()
   {
      return Collections.unmodifiableList(wires);
   }

   void setResolved(boolean resolved)
   {
      this.resolved = resolved;
   }

   @Override
   public boolean isResolved()
   {
      return resolved;
   }
}