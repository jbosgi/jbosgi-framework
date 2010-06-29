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

import java.util.Arrays;

import org.jboss.osgi.msc.metadata.ActivationPolicyMetaData;

/**
 * Activation policy value creator.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
*/
class ActivationPolicyMDValueCreator extends AbstractValueCreator<ActivationPolicyMetaData>
{
   private static final String INCLUDE = "include:=";
   private static final String EXCLUDE = "exclude:=";

   public ActivationPolicyMDValueCreator()
   {
      super(true);
   }

   protected ActivationPolicyMetaData useString(String attibute)
   {
      AbstractActivationPolicyMetaData aap = new AbstractActivationPolicyMetaData();
      String[] split = attibute.split(";");
      aap.setType(split[0]);
      if (split.length > 1)
         readDirective(aap, split[1]);
      if (split.length > 2)
         readDirective(aap, split[2]);
      return aap;
   }

   /**
    * Read the directive.
    *
    * @param aap the activation policy
    * @param directive the directive
    */
   protected void readDirective(AbstractActivationPolicyMetaData aap, String directive)
   {
      if (directive.startsWith(INCLUDE))
      {
         String[] includes = directive.substring(INCLUDE.length() + 1, directive.length() - 1).split(",");
         aap.setIncludes(Arrays.asList(includes));
      }
      else if (directive.startsWith(EXCLUDE))
      {
         String[] excludes = directive.substring(EXCLUDE.length() + 1, directive.length() - 1).split(",");
         aap.setExcludes(Arrays.asList(excludes));
      }
   }
}