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
package org.jboss.osgi.framework.plugin.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.AbstractUserBundle;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.FrameworkState;
import org.jboss.osgi.framework.loading.NativeLibraryProvider;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.framework.plugin.NativeCodePlugin;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.Parameter;
import org.jboss.osgi.metadata.ParameterizedAttribute;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

/**
 * The bundle native code plugin
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 11-Aug-2010
 */
public class NativeCodePluginImpl extends AbstractPlugin implements NativeCodePlugin {

    /**
     * The string that is to be replaced with the absolute path of the native library as specified by the core spec with the
     * org.osgi.framework.command.execpermission framework property.
     */
    private static final String ABSPATH_VARIABLE = "${abspath}";

    /** Maps an alias to an OSGi processor name */
    private static Map<String, String> processorAlias = new HashMap<String, String>();
    static {
        processorAlias.put("amd64", "x86-64");
        processorAlias.put("em64t", "x86-64");
        processorAlias.put("i386", "x86");
        processorAlias.put("i486", "x86");
        processorAlias.put("i586", "x86");
        processorAlias.put("i686", "x86");
        processorAlias.put("pentium", "x86");
        processorAlias.put("x86_64", "x86-64");
    }

    /** Maps an alias to an OSGi osname */
    private static Map<String, String> osAlias = new HashMap<String, String>();
    static {
        osAlias.put("hp-ux", "HPUX");
        osAlias.put("Mac OS", "MacOS");
        osAlias.put("Mac OS X", "MacOSX");
        osAlias.put("OS/2", "OS2");
        osAlias.put("procnto", "QNX");
        osAlias.put("SymbianOS", "Epoc32");
        osAlias.put("Win2000", "Windows2000");
        osAlias.put("Win2003", "Windows2003");
        osAlias.put("Win32", "Windows");
        osAlias.put("Win95", "Windows95");
        osAlias.put("Win98", "Windows98");
        osAlias.put("WinCE", "WindowsCE");
        osAlias.put("Windows 2000", "Windows2000");
        osAlias.put("Windows 2003", "Windows2003");
        osAlias.put("Windows 7", "Windows7");
        osAlias.put("Windows 95", "Windows95");
        osAlias.put("Windows 98", "Windows98");
        osAlias.put("Windows CE", "WindowsCE");
        osAlias.put("Windows NT", "WindowsNT");
        osAlias.put("Windows Server 2003", "Windows2003");
        osAlias.put("Windows Vista", "WindowsVista");
        osAlias.put("Windows XP", "WindowsXP");
        osAlias.put("WinNT", "WindowsNT");
        osAlias.put("WinVista", "WindowsVista");
        osAlias.put("WinXP", "WindowsXP");
    }

    public NativeCodePluginImpl(BundleManager bundleManager) {
        super(bundleManager);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void deployNativeCode(Deployment dep) {
        AbstractBundle bundleState = AbstractBundle.assertBundleState(dep.getAttachment(Bundle.class));
        if (bundleState == null)
            throw new IllegalStateException("Cannot obtain Bundle from: " + dep);

        OSGiMetaData osgiMetaData = bundleState.getOSGiMetaData();
        List<ParameterizedAttribute> nativeCodeParams = osgiMetaData.getBundleNativeCode();
        if (nativeCodeParams == null)
            throw new IllegalArgumentException("Cannot find Bundle-NativeCode header for: " + bundleState);

        // Find the matching parameters
        List<ParameterizedAttribute> matchedParams = new ArrayList<ParameterizedAttribute>();
        for (ParameterizedAttribute param : nativeCodeParams) {
            if (matchParameter(param))
                matchedParams.add(param);
        }

        // If no native clauses were selected in step 1, this algorithm is terminated
        // and a BundleException is thrown if the optional clause is not present
        if (matchedParams.size() == 0) {
            // [TODO] optional
            throw new IllegalStateException("No native clauses selected from: " + nativeCodeParams);
        }

        // The selected clauses are now sorted in the following priority order:
        // * osversion: floor of the osversion range in descending order, osversion not specified
        // * language: language specified, language not specified
        // * Position in the Bundle-NativeCode manifest header: lexical left to right
        if (matchedParams.size() > 1) {
            // [TODO] selected clauses are now sorted
        }

        NativeLibraryMetaData nativeLibraries = new NativeLibraryMetaData();
        dep.addAttachment(NativeLibraryMetaData.class, nativeLibraries);

        for (ParameterizedAttribute param : matchedParams) {
            Parameter osnameParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_OSNAME);
            Parameter procParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_PROCESSOR);
            // Parameter osversionParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_OSVERSION);

            List<String> osNames;
            if (osnameParam.isCollection())
                osNames = (List<String>) osnameParam.getValue();
            else
                osNames = Collections.singletonList((String) osnameParam.getValue());

            String libpath = param.getAttribute();
            String libsource = bundleState.getCanonicalName();

            NativeLibrary library = new NativeLibrary(osNames, libpath, libsource);

            // Processors
            if (procParam != null) {
                List<String> processors;
                if (procParam.isCollection())
                    processors = (List<String>) procParam.getValue();
                else
                    processors = Collections.singletonList((String) procParam.getValue());

                library.setProcessors(processors);
            }

            // [TODO] osVersions, languages, selectionFilter, optional
            // library.setOsVersions(osVersions);
            // library.setLanguages(languages);
            // library.setSelectionFilter(selectionFilter);
            // library.setOptional(optional);

            nativeLibraries.addNativeLibrary(library);
        }
    }

    @Override
    public void resolveNativeCode(AbstractUserBundle depBundle) {
        // Deployment dep = depBundle.getDeployment();
        // NativeLibraryMetaData libMetaData = dep.getAttachment(NativeLibraryMetaData.class);
        // if (libMetaData == null)
        // throw new IllegalStateException("Cannot obtain NativeLibraryMetaData from: " + dep);
        //
        // ModuleClassLoaderExt moduleClassLoader = (ModuleClassLoaderExt)depBundle.getModuleClassLoader();
        // if (moduleClassLoader == null)
        // throw new IllegalStateException("Cannot obtain ModuleClassLoader from: " + depBundle);
        //
        // // Add the native library mappings to the OSGiClassLoaderPolicy
        // for (NativeLibrary library : libMetaData.getNativeLibraries())
        // {
        // String libpath = library.getLibraryPath();
        // String libfile = new File(libpath).getName();
        // String libname = libfile.substring(0, libfile.lastIndexOf('.'));
        //
        // // Add the library provider to the policy
        // NativeLibraryProvider libProvider = new BundleNativeLibraryProvider(depBundle, libname, libpath);
        // moduleClassLoader.addNativeLibrary(libProvider);
        //
        // // [TODO] why does the TCK use 'Native' to mean 'libNative' ?
        // if (libname.startsWith("lib"))
        // {
        // libname = libname.substring(3);
        // libProvider = new BundleNativeLibraryProvider(depBundle, libname, libpath);
        // moduleClassLoader.addNativeLibrary(libProvider);
        // }
        // }
    }

    @SuppressWarnings("unchecked")
    private boolean matchParameter(ParameterizedAttribute param) {
        FrameworkState frameworkState = getBundleManager().getFrameworkState();
        String fwOSName = frameworkState.getProperty(Constants.FRAMEWORK_OS_NAME);
        String fwProcessor = frameworkState.getProperty(Constants.FRAMEWORK_PROCESSOR);
        // String fwOSVersion = frameworkState(Constants.FRAMEWORK_OS_VERSION);

        // Only select the native code clauses for which the following expressions all evaluate to true
        // * osname ~= [org.osgi.framework.os.name]
        // * processor ~= [org.osgi.framework.processor]
        // * osversion range includes [org.osgi.framework.os.version] or osversion is not specified
        // * language ~= [org.osgi.framework.language] or language is not specified
        // * selection-filter evaluates to true when using the values of the system properties or selection-filter is not
        // specified

        Parameter osnameParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_OSNAME);
        Parameter procParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_PROCESSOR);
        // Parameter osversionParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_OSVERSION);

        boolean match = (osnameParam != null);

        // osname ~= [org.osgi.framework.os.name]
        if (match == true && osnameParam != null) {
            List<String> osNames;
            if (osnameParam.isCollection())
                osNames = (List<String>) osnameParam.getValue();
            else
                osNames = Collections.singletonList((String) osnameParam.getValue());

            boolean osmatch = false;
            for (String osname : osNames) {
                osmatch = (osname.equalsIgnoreCase(fwOSName) || osname.equalsIgnoreCase(osAlias.get(fwOSName)));
                if (osmatch == true)
                    break;
            }

            match &= osmatch;
        }

        // processor ~= [org.osgi.framework.processor]
        match &= (procParam != null);
        if (match && procParam != null) {
            List<String> processors;
            if (procParam.isCollection())
                processors = (List<String>) procParam.getValue();
            else
                processors = Collections.singletonList((String) procParam.getValue());

            boolean procmatch = false;
            for (String proc : processors) {
                procmatch = (proc.equals(fwProcessor) || proc.equals(processorAlias.get(fwProcessor)));
                if (procmatch == true)
                    break;
            }

            match &= procmatch;
        }

        // [TODO] osversion range includes [org.osgi.framework.os.version] or osversion is not specified
        // [TODO] language ~= [org.osgi.framework.language] or language is not specified
        // [TODO] selection-filter evaluates to true when using the values of the system properties or selection-filter is not
        // specified
        return match;
    }

    public static class BundleNativeLibraryProvider implements NativeLibraryProvider {

        private AbstractUserBundle bundleState;
        private String libpath;
        private String libname;
        private File libraryFile;

        public BundleNativeLibraryProvider(AbstractUserBundle bundleState, String libname, String libpath) {
            this.bundleState = bundleState;
            this.libpath = libpath;
            this.libname = libname;

            // If a native code library in a selected native code clause cannot be found
            // within the bundle then the bundle must fail to resolve
            URL entryURL = bundleState.getEntry(libpath);
            if (entryURL == null)
                throw new IllegalStateException("Cannot find native library: " + libpath);
        }

        @Override
        public String getLibraryName() {
            return libname;
        }

        @Override
        public String getLibraryPath() {
            return libpath;
        }

        @Override
        public File getLibraryLocation() throws IOException {
            VirtualFile contentRoot = bundleState.getFirstContentRoot().getVirtualFile();
            if (libraryFile == null && contentRoot != null) {
                // Get the virtual file for entry for the library
                VirtualFile fileSource = contentRoot.getChild(libpath);

                // Create a unique local file location
                libraryFile = getUniqueLibraryFile(bundleState, libpath);
                libraryFile.deleteOnExit();

                // Copy the native library to the bundle storage area
                FileOutputStream fos = new FileOutputStream(libraryFile);
                VFSUtils.copyStream(fileSource.openStream(), fos);
                fos.close();

                handleExecPermission();
            }
            return libraryFile;
        }

        private void handleExecPermission() throws IOException {
            String epProp = bundleState.getBundleContext().getProperty(Constants.FRAMEWORK_EXECPERMISSION);
            if (epProp == null)
                return;

            StringBuilder command = new StringBuilder(epProp);
            int idx = command.indexOf(ABSPATH_VARIABLE);
            if (idx >= 0) {
                command.replace(idx, idx + ABSPATH_VARIABLE.length(), libraryFile.getAbsolutePath());
            }
            Process process = Runtime.getRuntime().exec(command.toString());
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                // Move ahead when interrupted
            }
        }

        private File getUniqueLibraryFile(final AbstractUserBundle bundleState, final String libpath) {
            BundleManager bundleManager = bundleState.getBundleManager();
            String timestamp = new SimpleDateFormat("-yyyyMMdd-HHmmssSSS").format(new Date(bundleState.getLastModified()));
            String uniquePath = new StringBuffer(libpath).insert(libpath.lastIndexOf("."), timestamp).toString();
            BundleStoragePlugin plugin = bundleManager.getPlugin(BundleStoragePlugin.class);
            return plugin.getDataFile(bundleState, uniquePath);
        }
    }
}