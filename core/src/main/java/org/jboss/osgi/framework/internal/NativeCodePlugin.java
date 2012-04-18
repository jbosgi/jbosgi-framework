/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.Parameter;
import org.jboss.osgi.metadata.ParameterizedAttribute;
import org.jboss.osgi.metadata.VersionRange;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;

/**
 * The bundle native code plugin
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 11-Aug-2010
 */
final class NativeCodePlugin extends AbstractPluginService<NativeCodePlugin> {

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

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();

    static void addService(ServiceTarget serviceTarget) {
        NativeCodePlugin service = new NativeCodePlugin();
        ServiceBuilder<NativeCodePlugin> builder = serviceTarget.addService(InternalServices.NATIVE_CODE_PLUGIN, service);
        builder.addDependency(org.jboss.osgi.framework.Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private NativeCodePlugin() {
    }

    @Override
    public NativeCodePlugin getValue() {
        return this;
    }

    void deployNativeCode(Deployment dep) {
        // Core 4.2 spec section 3.9: a bundle can be installed if the Bundle-NativeCode code header
        // doesn't match. Errors are reported during the resolution phase.

        // Add NativeLibraryMetaData to the deployment as a marker that the deployment contains
        // Native Code which needs to be processed. The actual processing of it happens in the
        // resolveNativeCode() method.
        NativeLibraryMetaData nativeLibraries = new NativeLibraryMetaData();
        dep.addAttachment(NativeLibraryMetaData.class, nativeLibraries);
    }

    void resolveNativeCode(UserBundleRevision userRev) throws BundleException {
        OSGiMetaData osgiMetaData = userRev.getOSGiMetaData();
        List<ParameterizedAttribute> params = osgiMetaData.getBundleNativeCode();
        if (params == null)
            throw MESSAGES.bundleCannotFindNativeCodeHeader(userRev);

        // Find the matching parameters
        List<ParameterizedAttribute> matchedParams = new ArrayList<ParameterizedAttribute>();
        for (ParameterizedAttribute param : params) {
            if (matchParameter(param))
                matchedParams.add(param);
        }

        Deployment dep = userRev.getDeployment();
        NativeLibraryMetaData nativeLibraries = dep.getAttachment(NativeLibraryMetaData.class);

        // If no native clauses were selected in step 1, this algorithm is terminated
        // and a BundleException is thrown if the optional clause is not present
        if (matchedParams.size() == 0) {
            if (params.size() > 0 && "*".equals(params.get(params.size() - 1).getAttribute())) {
                // This Bundle-NativeCode clause is optional but we're not selecting any native code clauses
                // so remove the marker deployment attachment
                dep.removeAttachment(NativeLibraryMetaData.class);

                return;
            }

            throw MESSAGES.bundleNoNativeCodeClauseSelected(params);
        }

        // The selected clauses are now sorted in the following priority order:
        // * osversion: floor of the osversion range in descending order, osversion not specified
        // * language: language specified, language not specified
        // * Position in the Bundle-NativeCode manifest header: lexical left to right
        // [TODO] sort the clauses

        for (ParameterizedAttribute param : matchedParams) {
            String libpath = param.getAttribute();
            NativeLibrary library = new NativeLibrary(libpath);
            nativeLibraries.addNativeLibrary(library);
        }
    }

    @SuppressWarnings({ "unchecked" })
    private List<String> getCollection(Object value) {
        if (value == null)
            return Collections.emptyList();

        if (value instanceof Collection)
            return new ArrayList<String>((Collection<String>) value);

        return Collections.singletonList(value.toString());
    }

    private boolean matchParameter(ParameterizedAttribute param) throws BundleException {
        // Only select the native code clauses for which the following expressions all evaluate to true
        // ('~=' stands for 'matches').
        // * osname ~= [org.osgi.framework.os.name]
        // * processor ~= [org.osgi.framework.processor]
        // * osversion range includes [org.osgi.framework.os.version] or osversion is not specified
        // * language ~= [org.osgi.framework.language] or language is not specified
        // * selection-filter evaluates to true when using the values of the system properties or selection-filter is not
        // specified

        // osname ~= [org.osgi.framework.os.name]
        Parameter osnameParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_OSNAME);
        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        BundleContext systemContext = bundleManager.getSystemBundle().getBundleContext();
        boolean match = (osnameParam != null);
        if (match == true && osnameParam != null) {
            String fwOSName = systemContext.getProperty(Constants.FRAMEWORK_OS_NAME);

            boolean osmatch = false;
            Collection<String> osNames = getCollection(osnameParam.getValue());
            for (String osname : osNames) {
                osmatch = (osname.equalsIgnoreCase(fwOSName) || osname.equalsIgnoreCase(osAlias.get(fwOSName)));
                if (osmatch == true)
                    break;
            }

            match &= osmatch;
        }

        // processor ~= [org.osgi.framework.processor]
        Parameter procParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_PROCESSOR);
        match &= (procParam != null);
        if (match && procParam != null) {
            String fwProcessor = systemContext.getProperty(Constants.FRAMEWORK_PROCESSOR);

            boolean procmatch = false;
            List<String> processors = getCollection(procParam.getValue());
            for (String proc : processors) {
                procmatch = (proc.equals(fwProcessor) || proc.equals(processorAlias.get(fwProcessor)));
                if (procmatch == true)
                    break;
            }

            match &= procmatch;
        }

        // osversion range includes [org.osgi.framework.os.version] or osversion is not specified
        Parameter osversionParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_OSVERSION);
        if (match && osversionParam != null) {
            String fwOSVersion = systemContext.getProperty(Constants.FRAMEWORK_OS_VERSION);

            boolean versionMatch = false;
            Version currentVersion = Version.parseVersion(fwOSVersion);

            for (String versionRange : getCollection(osversionParam.getValue())) {
                VersionRange vr;
                vr = VersionRange.parse(versionRange);
                if (vr.isInRange(currentVersion)) {
                    versionMatch = true;
                    break;
                }
            }

            match &= versionMatch;
        }

        // language ~= [org.osgi.framework.language] or language is not specified
        Parameter languageParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_LANGUAGE);
        if (match && languageParam != null) {
            String fwLanguage = systemContext.getProperty(Constants.FRAMEWORK_LANGUAGE);

            boolean languageMatch = false;
            for (String language : getCollection(languageParam.getValue())) {
                if (language.equals(fwLanguage)) {
                    languageMatch = true;
                    break;
                }
            }

            match &= languageMatch;
        }

        // selection-filter evaluates to true when using the values of the system properties or selection-filter is not specified
        Parameter filterSelectionParam = param.getAttribute(Constants.SELECTION_FILTER_ATTRIBUTE);
        if (match && filterSelectionParam != null) {
            boolean filterMatch = false;
            Dictionary<String, Object> frameworkProps = new Hashtable<String, Object>(bundleManager.getProperties());
            for (String filterSpec : getCollection(filterSelectionParam.getValue())) {
                try {
                    Filter filter = FrameworkUtil.createFilter(filterSpec);
                    if (filter.match(frameworkProps)) {
                        filterMatch = true;
                        break;
                    }

                } catch (InvalidSyntaxException ex) {
                    throw MESSAGES.bundleInvalidFilterExpression(ex, filterSpec);
                }
            }

            match &= filterMatch;
        }

        return match;
    }

    static class BundleNativeLibraryProvider implements NativeLibraryProvider {
        private final HostBundleState hostBundle;
        private final String libname;
        private final String libpath;
        private final URL libURL;
        private File libraryFile;

        BundleNativeLibraryProvider(HostBundleRevision hostrev, String libname, String libpath) {
            this.hostBundle = hostrev.getBundleState();
            this.libpath = libpath;
            this.libname = libname;

            // If a native code library in a selected native code clause cannot be found
            // within the bundle or its fragments then the bundle must fail to resolve
            String path;
            String filename;
            int idx = libpath.lastIndexOf('/');
            if (idx >= 0) {
                path = libpath.substring(0, idx);
                filename = libpath.substring(idx + 1);
            } else {
                path = "";
                filename = libpath;
            }
            Enumeration<URL> urls = hostrev.findResolvedEntries(path, filename, false);
            if (urls == null || urls.hasMoreElements() == false)
                throw MESSAGES.illegalStateCannotFindNativeLibrary(libpath);

            this.libURL = urls.nextElement();
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
            if (libraryFile == null) {
                // Create a unique local file location
                libraryFile = getUniqueLibraryFile(hostBundle, libpath);
                libraryFile.deleteOnExit();

                // Copy the native library to the bundle storage area
                FileOutputStream fos = new FileOutputStream(libraryFile);
                VFSUtils.copyStream(libURL.openStream(), fos);
                fos.close();

                handleExecPermission();
            }
            return libraryFile;
        }

        private void handleExecPermission() throws IOException {
            String epProp = hostBundle.getBundleContext().getProperty(Constants.FRAMEWORK_EXECPERMISSION);
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

        private File getUniqueLibraryFile(final UserBundleState userBundle, final String libpath) {
            BundleStoragePlugin storagePlugin = userBundle.getFrameworkState().getBundleStoragePlugin();
            return storagePlugin.getDataFile(userBundle.getBundleId(), libpath);
        }
    }
}