/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;
import static org.jboss.osgi.framework.internal.InternalConstants.NATIVE_LIBRARY_METADATA_KEY;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.IntegrationConstants;
import org.jboss.osgi.framework.spi.NativeCode;
import org.jboss.osgi.framework.spi.NativeLibraryProvider;
import org.jboss.osgi.framework.spi.StorageManager;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.Parameter;
import org.jboss.osgi.metadata.ParameterizedAttribute;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;

/**
 * The bundle native code plugin
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 11-Aug-2010
 */
public final class NativeCodeImpl implements NativeCode {

    /**
     * The string that is to be replaced with the absolute path of the native library as specified by the core spec with the
     * org.osgi.framework.command.execpermission framework property.
     */
    private static final String ABSPATH_VARIABLE = "${abspath}";

    /** Maps an alias to an OSGi processor name */
    private static Map<String, String> processorAlias = new HashMap<String, String>();
    static {
        // http://www.osgi.org/Specifications/Reference#processor
        processorAlias.put("psc1k", "ignite");
        processorAlias.put("power", "PowerPC");
        processorAlias.put("ppc", "PowerPC");
        processorAlias.put("ppc64", "PowerPC-64");
        processorAlias.put("ppc64le", "PowerPC-64-LE");
        processorAlias.put("pentium", "x86");
        processorAlias.put("i386", "x86");
        processorAlias.put("i486", "x86");
        processorAlias.put("i586", "x86");
        processorAlias.put("i686", "x86");
        processorAlias.put("amd64", "x86-64");
        processorAlias.put("em64t", "x86-64");
        processorAlias.put("x86_64", "x86-64");
    }

    /** Maps an alias to an OSGi osname */
    private static Map<String, String []> osAlias = new HashMap<String, String []>();
    static {
        // http://www.osgi.org/Specifications/Reference#os
        osAlias.put("SymbianOS", new String[] { "Epoc32" });
        osAlias.put("hp-ux", new String[] { "HPUX" });
        osAlias.put("Mac OS", new String[] { "MacOS" });
        osAlias.put("Mac OS X", new String[] { "MacOSX" });
        osAlias.put("OS/2", new String[] { "OS2" });
        osAlias.put("procnto", new String[] { "QNX" });

        osAlias.put("Windows 95", new String[] { "Windows95" });
        osAlias.put("Win95", new String[] { "Windows95" });

        osAlias.put("Windows 98", new String[] { "Windows98" });
        osAlias.put("Win98", new String[] { "Windows98" });

        osAlias.put("Windows NT", new String[] { "WindowsNT" });
        osAlias.put("WinNT", new String[] { "WindowsNT" });

        osAlias.put("Windows CE", new String[] { "WindowsCE" });
        osAlias.put("WinCE", new String[] { "WindowsCE" });

        osAlias.put("Windows 2000", new String[] { "Windows2000" });
        osAlias.put("Win2000", new String[] { "Windows2000" });

        osAlias.put("Windows XP", new String[] { "WindowsXP" });
        osAlias.put("WinXP", new String[] { "WindowsXP" });

        osAlias.put("Windows 2003", new String[] { "Windows2003" });
        osAlias.put("Win2003", new String[] { "Windows2003" });
        osAlias.put("Windows Server 2003", new String[] { "Windows2003" });

        osAlias.put("Windows Vista", new String[] { "WindowsVista" });
        osAlias.put("WinVista", new String[] { "WindowsVista" });

        osAlias.put("Windows 7", new String[] { "Windows7" });
        osAlias.put("Win7", new String[] { "Windows7" });

        osAlias.put("Windows 8", new String[] { "Windows8" });
        osAlias.put("Win8", new String[] { "Windows8" });

        osAlias.put("Windows Server 2008", new String[] { "WindowsServer2008" });
        osAlias.put("Windows Server 2012", new String[] { "WindowsServer2012" });

        osAlias.put("Win32", new String[] { "Windows95", "Windows98", "WindowsNT", "WindowsCE", "Windows2000", "WindowsXP",
                "Windows2003", "WindowsVista", "Windows7", "Windows8", "WindowsServer2008", "WindowsServer2012" });
    }

    private final BundleManagerPlugin bundleManager;

    public NativeCodeImpl(BundleManager bundleManager) {
        this.bundleManager = (BundleManagerPlugin) bundleManager;
    }

    @Override
    public void deployNativeCode(Deployment dep) {
        // Core 4.2 spec section 3.9: a bundle can be installed if the Bundle-NativeCode code header
        // doesn't match. Errors are reported during the resolution phase.

        // Add NativeLibraryMetaData to the deployment as a marker that the deployment contains
        // Native Code which needs to be processed. The actual processing of it happens in the
        // resolveNativeCode() method.
        NativeLibraryMetaData nativeLibraries = new NativeLibraryMetaData();
        dep.putAttachment(NATIVE_LIBRARY_METADATA_KEY, nativeLibraries);
    }

    @Override
    public void resolveNativeCode(XBundleRevision brev) throws BundleException {
        UserBundleRevision userRev = UserBundleRevision.assertBundleRevision(brev);
        OSGiMetaData metaData = userRev.getAttachment(IntegrationConstants.OSGI_METADATA_KEY);
        List<ParameterizedAttribute> params = metaData.getBundleNativeCode();
        if (params.isEmpty())
            throw MESSAGES.cannotFindNativeCodeHeader(userRev);

        // Find the matching parameters
        List<ParameterizedAttribute> matchedParams = new ArrayList<ParameterizedAttribute>();
        for (ParameterizedAttribute param : params) {
            if (matchParameter(param))
                matchedParams.add(param);
        }

        Deployment dep = userRev.getDeployment();
        NativeLibraryMetaData nativeLibraries = dep.getAttachment(NATIVE_LIBRARY_METADATA_KEY);

        // If no native clauses were selected in step 1, this algorithm is terminated
        // and a BundleException is thrown if the optional clause is not present
        if (matchedParams.size() == 0) {
            if (params.size() > 0 && "*".equals(params.get(params.size() - 1).getAttribute())) {
                // This Bundle-NativeCode clause is optional but we're not selecting any native code clauses
                // so remove the marker deployment attachment
                dep.removeAttachment(NATIVE_LIBRARY_METADATA_KEY);

                return;
            }

            throw MESSAGES.noNativeCodeClauseSelected(params);
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
    private List<String> getCollection(Object value, boolean allowsplit) {
        if (value == null)
            return Collections.emptyList();

        if (value instanceof Collection)
            return new ArrayList<String>((Collection<String>) value);

        if (value instanceof String && allowsplit) {
            String valueString = (String) value;
            String[] split = valueString.split(",\\s");
            return Arrays.asList(split);
        }

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
        BundleContext systemContext = bundleManager.getSystemBundle().getBundleContext();
        boolean match = (osnameParam != null);
        if (match == true && osnameParam != null) {
            String fwOSName = systemContext.getProperty(Constants.FRAMEWORK_OS_NAME);

            boolean osmatch = false;
            Collection<String> osNames = getCollection(osnameParam.getValue(), true);
            for (String osname : osNames) {
                osmatch = osname.equalsIgnoreCase(fwOSName);
                if(!osmatch) {
                    String [] canonicalNames = osAlias.get(fwOSName);
                    if (canonicalNames != null) {
                        for (String canonicalName : canonicalNames) {
                            osmatch = osname.equalsIgnoreCase(canonicalName);
                            if (osmatch) {
                                break;
                            }
                        }
                    }
                }
                if (osmatch) {
                    break;
                }
            }

            match &= osmatch;
        }

        // processor ~= [org.osgi.framework.processor]
        Parameter procParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_PROCESSOR);
        match &= (procParam != null);
        if (match && procParam != null) {
            String fwProcessor = systemContext.getProperty(Constants.FRAMEWORK_PROCESSOR);

            boolean procmatch = false;
            List<String> processors = getCollection(procParam.getValue(), true);
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

            String versionRange = (String) osversionParam.getValue();
            if (versionRange != null) {
                VersionRange vr = new VersionRange(versionRange);
                if (vr.includes(currentVersion)) {
                    versionMatch = true;
                }
            }

            match &= versionMatch;
        }

        // language ~= [org.osgi.framework.language] or language is not specified
        Parameter languageParam = param.getAttribute(Constants.BUNDLE_NATIVECODE_LANGUAGE);
        if (match && languageParam != null) {
            String fwLanguage = systemContext.getProperty(Constants.FRAMEWORK_LANGUAGE);

            boolean languageMatch = false;
            for (String language : getCollection(languageParam.getValue(), true)) {
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
            String filterSpec = (String) filterSelectionParam.getValue();
            if (filterSpec != null) {
                try {
                    Filter filter = FrameworkUtil.createFilter(filterSpec);
                    if (filter.match(frameworkProps)) {
                        filterMatch = true;
                    }
                } catch (InvalidSyntaxException ex) {
                    throw MESSAGES.invalidFilterExpression(ex, filterSpec);
                }
            }

            match &= filterMatch;
        }

        return match;
    }

    static class NativeLibraryProviderImpl implements NativeLibraryProvider {
        private final UserBundleState hostBundle;
        private final String libname;
        private final String libpath;
        private final URL libURL;
        private File libraryFile;

        NativeLibraryProviderImpl(HostBundleRevision hostrev, String libname, String libpath) {
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
            String commandString = command.toString();
            Process process = Runtime.getRuntime().exec(commandString);
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                // Move ahead when interrupted
            }
            int exitCode = process.exitValue();
            if(exitCode != 0) {
                throw new IOException("FRAMEWORK_EXECPERMISSION '" + commandString + "' exited with " + exitCode);
            }
        }

        private File getUniqueLibraryFile(UserBundleState userBundle, String libpath) {
            StorageManager storagePlugin = userBundle.getFrameworkState().getStorageManager();
            return storagePlugin.getDataFile(userBundle.getBundleId(), libpath);
        }
    }
}