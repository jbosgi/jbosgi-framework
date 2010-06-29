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
package org.jboss.osgi.msc.metadata.internal;

import java.io.Serializable;

import org.jboss.osgi.msc.metadata.ListenerMetaData;

/**
 * Simple listener meta data.
 * Referencing ref bean as a reference listener.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 */
public class AbstractListenerMetaData implements ListenerMetaData, Serializable
{
   private static final long serialVersionUID = 1l;

   private String ref;
   private String bindMethod;
   private String unbindMethod;

   public String getRef()
   {
      return ref;
   }

   public String getBindMethod()
   {
      return bindMethod;
   }

   public String getUnbindMethod()
   {
      return unbindMethod;
   }

   public void setRef(String ref)
   {
      this.ref = ref;
   }

   public void setBindMethod(String bindMethod)
   {
      this.bindMethod = bindMethod;
   }

   public void setUnbindMethod(String unbindMethod)
   {
      this.unbindMethod = unbindMethod;
   }

}
