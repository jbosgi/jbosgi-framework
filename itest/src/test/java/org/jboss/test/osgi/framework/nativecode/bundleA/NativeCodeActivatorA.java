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
package org.jboss.test.osgi.framework.nativecode.bundleA;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class NativeCodeActivatorA implements BundleActivator {

    private static Map<String, String> osAliases = new HashMap<String, String>();

    static {
        osAliases.put("SymbianOS", "Epoc32");
        osAliases.put("hp-ux", "HPUX");
        osAliases.put("Linux", "Linux");
        osAliases.put("Mac OS", "MacOS");
        osAliases.put("Mac OS X", "MacOSX");
        osAliases.put("OS/2", "OS2");
        osAliases.put("procnto", "QNX");
        // map any winz stuff to plain windows
        osAliases.put("Win2000", "Windows");
        osAliases.put("Win2003", "Windows");
        osAliases.put("Win32", "Windows");
        osAliases.put("Win95", "Windows");
        osAliases.put("Win98", "Windows");
        osAliases.put("WinCE", "Windows");
        osAliases.put("Windows 2000", "Windows");
        osAliases.put("Windows 2003", "Windows");
        osAliases.put("Windows 7", "Windows");
        osAliases.put("Windows 95", "Windows");
        osAliases.put("Windows 98", "Windows");
        osAliases.put("Windows CE", "Windows");
        osAliases.put("Windows NT", "Windows");
        osAliases.put("Windows Server 2003", "Windows");
        osAliases.put("Windows Vista", "Windows");
        osAliases.put("Windows XP", "Windows");
        osAliases.put("WinNT", "Windows");
        osAliases.put("WinVista", "Windows");
        osAliases.put("WinXP", "Windows");
    }

    public void start(BundleContext context) throws BundleException {
        Bundle bundle = context.getBundle();
        try {
            System.loadLibrary("Native");
            throw new IllegalStateException("UnsatisfiedLinkError expected");
        } catch (UnsatisfiedLinkError ex) {
            String exmsg = ex.getMessage();
            long bundleid = bundle.getBundleId();
            String os = System.getProperty("os.name");
            String osAlias = osAliases.get(os);
            String suffix = osAlias != null ? osAlias.toLowerCase() : "";
            if ("".equals(suffix))
                System.err.println("No such OS mapped to alias: " + os);

            String substr = "osgi-store" + File.separator + "bundle-" + bundleid + File.separator + suffix;
            if (exmsg.indexOf(substr) < 0)
                throw new UnsatisfiedLinkError("Cannot find '" + substr + "' in '" + exmsg + "'");
        }
    }

    public void stop(BundleContext context) {
    }
}