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

/**
 * ValueCreator holder.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 */
public class ValueCreatorUtil
{
   public static StringValueCreator STRING_VC = new StringValueCreator();
   public static IntegerValueCreator INTEGER_VC = new IntegerValueCreator();
   public static BooleanValueCreator BOOLEAN_VC = new BooleanValueCreator();
   public static VersionValueCreator VERSION_VC = new VersionValueCreator();
   public static VersionRangeValueCreator VERSION_RANGE_VC = new VersionRangeValueCreator();
   public static URLValueCreator URL_VC = new URLValueCreator();
   public static StringListValueCreator STRING_LIST_VC = new StringListValueCreator();
   public static ParameterizedAttributeValueCreator PARAM_ATTRIB_VC = new ParameterizedAttributeValueCreator();
   public static ParameterizedAttributeListValueCreator QNAME_ATTRIB_LIST_VC = new QNameAttributeListValueCreator();
   public static ParameterizedAttributeListValueCreator PATH_ATTRIB_LIST_VC = new PathAttributeListValueCreator();
   public static PackageAttributeListValueCreator PACKAGE_LIST_VC = new PackageAttributeListValueCreator();
   public static ActivationPolicyMDValueCreator ACTIVATION_POLICY_VC = new ActivationPolicyMDValueCreator();
}
