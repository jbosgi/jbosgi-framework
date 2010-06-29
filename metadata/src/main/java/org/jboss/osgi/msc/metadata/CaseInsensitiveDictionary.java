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
package org.jboss.osgi.msc.metadata;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.jboss.util.collection.Iterators;

/**
 * CaseInsensitiveDictionary.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 1.1 $
 */
@SuppressWarnings("rawtypes")
public class CaseInsensitiveDictionary extends Hashtable
{
   private static final long serialVersionUID = 5802491129524016545L;

   /** The delegate dictionary */
   private Dictionary<String, Object> delegate;

   /** The original keys */
   private Set<String> originalKeys;

   /**
    * Create a new CaseInsensitiveDictionary.
    * 
    * @param delegate the delegate
    */
   @SuppressWarnings("unchecked")
   public CaseInsensitiveDictionary(Dictionary delegate)
   {
      if (delegate == null)
         throw new IllegalArgumentException("Null delegaqte");

      this.delegate = new Hashtable<String, Object>(delegate.size());
      this.originalKeys = Collections.synchronizedSet(new HashSet<String>());
      Enumeration<String> e = delegate.keys();
      while (e.hasMoreElements())
      {
         String key = e.nextElement();
         if (get(key) != null)
            throw new IllegalArgumentException("Properties contain duplicates with varying case for key=" + key + " : " + delegate);

         this.delegate.put(key.toLowerCase(), delegate.get(key));
         originalKeys.add(key);
      }
   }

   public Enumeration<Object> elements()
   {
      return delegate.elements();
   }

   @SuppressWarnings("unchecked")
   public synchronized boolean equals(Object obj)
   {
      if (obj == this)
         return true;
      if (obj == null || obj instanceof Dictionary == false)
         return false;

      Dictionary<String, Object> other = (Dictionary)obj;

      if (size() != other.size())
         return false;
      if (isEmpty())
         return true;

      for (String key : originalKeys)
      {
         if (get(key).equals(other.get(key)))
            return false;
      }
      return true;
   }

   public Object get(Object key)
   {
      if (key instanceof String)
         key = ((String)key).toLowerCase();
      return delegate.get(key);
   }

   public int hashCode()
   {
      return delegate.hashCode();
   }

   public boolean isEmpty()
   {
      return delegate.isEmpty();
   }

   @SuppressWarnings("unchecked")
   public Enumeration<String> keys()
   {
      return Iterators.toEnumeration(originalKeys.iterator());
   }

   public Object put(Object key, Object value)
   {
      throw new UnsupportedOperationException("immutable");
   }

   public Object remove(Object key)
   {
      throw new UnsupportedOperationException("immutable");
   }

   public int size()
   {
      return delegate.size();
   }

   public String toString()
   {
      return delegate.toString();
   }
}
